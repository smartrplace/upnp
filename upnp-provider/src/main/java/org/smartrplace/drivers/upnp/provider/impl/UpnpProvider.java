/**
 * Copyright 2018 Smartrplace UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartrplace.drivers.upnp.provider.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.drivers.upnp.tools.Message;
import org.smartrplace.drivers.upnp.tools.MessageNotify;
import org.smartrplace.drivers.upnp.tools.MessageResponse;
import org.smartrplace.drivers.upnp.tools.MessageSearch;
import org.smartrplace.drivers.upnp.tools.NetworkUtils;
import org.smartrplace.drivers.upnp.tools.NotifyType;

@Component(immediate = true)
public class UpnpProvider {

	private static final String XML_DESCRIPTION_ADDRESS = "/upnp/ogema.xml";
	static final String ICON_ADDRESS = "/upnp/favicon.png";
	final static Logger logger = LoggerFactory.getLogger(UpnpProvider.class);
	final static int MAX_AGE = 1800; // FIXME
	final static InetAddress MULTICAST_ADDRESS;
	final static String SEARCH_MSG_IDENTIFIER = "M-SEARCH * HTTP/1.1\r\n";
	final static int MULTICAST_PORT = 1900;
	volatile static int OWN_SSL_PORT;
	volatile static int OWN_NON_TLS_PORT;
	volatile Thread listener = null;
	private volatile ScheduledExecutorService executor;
	private volatile MulticastSocket serverSocket;
	private volatile ScheduledFuture<?> scheduledFuture;
	volatile UUID uuid;

	@Reference
	HttpService httpService;

	static {
		try {
			MULTICAST_ADDRESS = InetAddress.getByName("239.255.255.250");
		} catch (UnknownHostException e) {
			throw new RuntimeException("???",e);
		}
	}

	@Activate
	public synchronized void activate(BundleContext ctx, Map<String, ?> config) {
		String port = ctx.getProperty("org.osgi.service.http.port.secure");
		try {
			OWN_SSL_PORT = Integer.parseInt(port);
		} catch (NumberFormatException | NullPointerException e) {
			OWN_SSL_PORT = 8443;
		}
		port = ctx.getProperty("org.osgi.service.http.port");
		try {
			OWN_NON_TLS_PORT = Integer.parseInt(port);
		} catch (NumberFormatException | NullPointerException e) {
			OWN_NON_TLS_PORT = 8080;
		}
		if (config.containsKey("upnpUUID"))
			uuid = UUID.fromString((String) config.get("upnpUUID"));
		else {
			uuid = UUID.randomUUID();
			final ConfigurationAdmin configAdmin = getConfigAdmin(ctx);
			if (configAdmin == null) {
				logger.warn("ConfigurationAdmin not found, cannot persist UPNP id");
			} else {
				try {
					final Configuration cfg = configAdmin.getConfiguration(UpnpProvider.class.getName());
					Dictionary<String, Object> props = cfg.getProperties();
					if (props == null)
						props = new Hashtable<>(2);
					props.put("upnpUUID", uuid.toString());
					cfg.update(props);
				} catch (IOException e) {
					logger.warn("Failed to persists UPNP id",e);
				}
			}
		}
		try {
			serverSocket= new MulticastSocket(MULTICAST_PORT);
			serverSocket.setReuseAddress(true); // ?
		} catch (IOException e1) {
			throw new RuntimeException("Server could not be initialized",e1);
		}
		executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r,"SSDP_device_announcement_thread");
				thread.setDaemon(true);
				return thread;
			}
		});
		try {
			// TODO use whiteboard service instead?
			httpService.registerServlet(XML_DESCRIPTION_ADDRESS, new XmlDescriptorServlet(this), null, null);
			// use fragment bundles for configuration
			final Enumeration<URL> urls = ctx.getBundle().findEntries("/", "upnp.*", false);
			if (urls != null && urls.hasMoreElements()) {
				httpService.registerResources(ICON_ADDRESS, urls.nextElement().getPath().substring(0), null);
			} else {
				logger.warn("No icon found for UPNP service");
			}
		} catch (ServletException | NamespaceException e2) {
			throw new RuntimeException("Could not register descriptor servlet",e2);
		}
		listener = new Thread(new Runnable() {

			@Override
			public void run() {
				listen();
			}
		});
		listener.start();
		Runnable periodicCommand = new Runnable() {

			@Override
			public void run() {
				MessageNotify notify;
				try {
					notify = new MessageNotify(uuid.toString(), NotifyType.AVAILABLE, getOwnLocationNoTLS(), MAX_AGE, 1);
				} catch (UnknownHostException | MalformedURLException | SocketException e1) {
					logger.debug("Could not determine own host IP",e1);
					return;
				}
				byte[] sendData = notify.get().getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, MULTICAST_ADDRESS, MULTICAST_PORT);
				try {
					serverSocket.send(sendPacket);
				} catch (IOException e) {
					logger.warn("Could not send notify message",e);
				}
			}
		};

		// instead of sending many requests at once (due to possibility of package loss with UDP), simply send more often
		scheduledFuture = executor.scheduleAtFixedRate(periodicCommand, 2000, MAX_AGE*1000/4, TimeUnit.MILLISECONDS);

	}

	@org.apache.felix.scr.annotations.Modified
	protected void modified(BundleContext ctx, Map<String,?> config) {
		// only here to avoid component restart when we update the configuration property
	}

	@Deactivate
	public synchronized void stop() {
		try {
			httpService.unregister(XML_DESCRIPTION_ADDRESS);
			httpService.unregister(ICON_ADDRESS);
		} catch (IllegalArgumentException e) { /* ignore */ }
		if (scheduledFuture != null)
			scheduledFuture.cancel(true);
		scheduledFuture = null;
		if (executor != null) {
			Runnable notifyLeaving = new Runnable() {

				@Override
				public void run() {
					MessageNotify notify;
					try {
						notify = new MessageNotify(uuid.toString(), NotifyType.BYE_BYE, getOwnLocationNoTLS(), MAX_AGE, 1);
					} catch (UnknownHostException | MalformedURLException | SocketException e1) {
						logger.debug("Could not determine own host IP",e1);
						return;
					}
					byte[] sendData = notify.get().getBytes();
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, MULTICAST_ADDRESS, MULTICAST_PORT);
					try {
						serverSocket.send(sendPacket);
					} catch (IOException e) {
						logger.warn("Could not send notify message",e);
					}
				}
			};
			executor.submit(notifyLeaving);
		}
		if (listener != null)
			listener.interrupt();
		listener = null;
		if (executor != null) {
			executor.shutdown();
			try {
				executor.awaitTermination(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) { /* ignore */	}
			if (!executor.isTerminated()) {
				executor.shutdownNow();
				try {
					executor.awaitTermination(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) { /* ignore */	}
			}
			if (!executor.isTerminated())
				logger.warn("Executor did not shut down properly");
		}
		executor = null;

		if (serverSocket != null) {
			serverSocket.close();
		}
		serverSocket = null;
	}

	// listen for multicast search requests
	void listen() {
		int failCounter = 0;
		// must bind receive side
		try {
			serverSocket.joinGroup(MULTICAST_ADDRESS);
		} catch (Exception e) {
			failCounter++;
			logger.warn("Failed to connect to SSDP multicast thread",e);
			if (failCounter > 15)
				failCounter = 15;
			try {
				Thread.sleep(failCounter^2 * 2000);
			} catch (InterruptedException e1) {
				logger.info("Thread interrupted, stop trying to connect to SSDP multicast thread");
				return;
			}
		}
		logger.info("UPNP server registered");
		failCounter = 0;
		NavigableMap<Long, String> lastSearchRequests = new TreeMap<>();
		long MAX_DIFF = 10000; // reply to one given sender at most once every 10s
		while (!Thread.interrupted()) {
			try {
				byte[] receiveData = new byte[1024];
				final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				// interruptible -> close
				serverSocket.receive(receivePacket);
				if (Thread.interrupted()) {
					logger.debug("interrupted and leaving");
					break;
				}
				String sentence = new String(receivePacket.getData());
				String address = receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort();
				if (!sentence.startsWith(SEARCH_MSG_IDENTIFIER))
					continue;
				logger.trace("UPNP multicast received from {}", address);
				final MessageSearch msg;
				try {
					msg = (MessageSearch) Message.parse(sentence);
				} catch (IllegalArgumentException | NullPointerException e) {
					logger.debug("Invalid message",e);
					continue;
				}
				if (!msg.rootDevice())
					continue;
//				InetAddress ownHost;
//				try {
//					ownHost = InetAddress.getLocalHost();
//				} catch (UnknownHostException e) {
//					logger.error("Could not determine own host",e);
//					continue;
//				}
				long now = System.currentTimeMillis();
				lastSearchRequests.subMap(0L, now-MAX_DIFF).clear();
				long lastFromThisSender = -1;
				Iterator<Map.Entry<Long,String>> it = lastSearchRequests.descendingMap().entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<Long,String> entry = it.next();
					if (entry.getValue().equals(address)) {
						lastFromThisSender = entry.getKey();
						break;
					}
				}
				if (lastFromThisSender > 0) {
					logger.trace("Multiple requests from the same sender {}, ignoring this",address);
					continue;
				}
				lastSearchRequests.put(now, address);

				final MessageResponse response = new MessageResponse(uuid.toString(),getOwnLocationNoTLS(), MAX_AGE, 1); // TODO
				Runnable runnable = new Runnable() {

					@Override
					public void run() {
						int delay = Math.max(1,(int) (Math.random() * msg.maxDelay));
						try {
							Thread.sleep(delay * 1000);
						} catch (InterruptedException e1) {
							Thread.currentThread().interrupt();
							return;
						}
						byte[] sendData = response.get().getBytes();
						// send response to correct address
						InetAddress recipient = receivePacket.getAddress();
						int recipientPort = receivePacket.getPort();
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, recipient, recipientPort);
						try {
							serverSocket.send(sendPacket);
							logger.debug("Response sent to {}:{}",recipient.getHostAddress(),recipientPort);
						} catch (IOException e) {
							logger.warn("Could not send response",e);
						}
					}
				};
				executor.submit(runnable);
				executor.submit(runnable); // send twice, this is UDP... other devices send up to 6 times the same message
				failCounter = 0;
			}
			catch (Exception ee) {
				if (Thread.interrupted())
					break;
				logger.error("Error in UDP server thread... trying again later",ee);
				failCounter++;
				if (failCounter > 15)
					failCounter = 15;
				try {
					Thread.sleep(failCounter^2 * 2000);
				} catch (InterruptedException e) {
					break;
				}
			}

		}
		Thread.currentThread().interrupt();
		logger.info("Device stops listening for SSDP requests");
	}

	static URL getOwnLocation() throws MalformedURLException, UnknownHostException, SocketException {
		return new URL(getBaseUrl() + XML_DESCRIPTION_ADDRESS); // Note: this file is generated on the fly
	}

	static URL getOwnLocationNoTLS() throws MalformedURLException, UnknownHostException, SocketException {
		return new URL(getBaseUrlNoTLS() + XML_DESCRIPTION_ADDRESS); // Note: this file is generated on the fly
	}

	static String getBaseUrl() throws UnknownHostException, SocketException {
		return "https://" + NetworkUtils.getHANAddress(logger).getHostAddress() + ":" + OWN_SSL_PORT;
	}

	static String getBaseUrlNoTLS() throws UnknownHostException, SocketException {
		return "http://" +  NetworkUtils.getHANAddress(logger).getHostAddress() + ":" + OWN_NON_TLS_PORT;
	}

	private static ConfigurationAdmin getConfigAdmin(final BundleContext ctx) {
		final ServiceReference<ConfigurationAdmin> ref = ctx.getServiceReference(ConfigurationAdmin.class);
		if (ref == null)
			return null;
		return ctx.getService(ref);

	}

}
