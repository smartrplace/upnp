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
package org.smartrplace.drivers.upnp.client.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.drivers.upnp.tools.Message;
import org.smartrplace.drivers.upnp.tools.MessageNotify;
import org.smartrplace.drivers.upnp.tools.MessageSearch;
import org.smartrplace.drivers.upnp.tools.MessageType;
import org.smartrplace.drivers.upnp.tools.NetworkUtils;
import org.smartrplace.drivers.upnp.tools.NotifyType;
import org.smartrplace.drivers.upnp.tools.exec.ScalingThreadPoolExecutor;
import org.w3c.dom.Document;

// TODO stand-alone rundir?
@Component(immediate = true)
public class UpnpClient extends HttpServlet {

	private static final long serialVersionUID = 1L;
	final static Logger logger = LoggerFactory.getLogger(UpnpClient.class);
	final static String URL_BASE = "/org/smartrplace/drivers/upnp/overview";
	private static final int SCAN_TIMEOUT = 10000;
	final static InetAddress MULTICAST_ADDRESS;
	final static int MULTICAST_PORT = 1900;
	volatile String lastUpdateMessage;
	volatile boolean lastUpdateSuccess;
	private volatile MulticastSocket multiSocket;
	final ConcurrentMap<String,RemoteDevice> devices = new ConcurrentHashMap<>();;
	private Thread discoveryMulti; 
	private Thread discovery;
	private Thread initThread;
	ExecutorService es;
	DocumentBuilderFactory factory;
	
	static {
		try {
			MULTICAST_ADDRESS = InetAddress.getByName("239.255.255.250");
		} catch (UnknownHostException e) {
			throw new RuntimeException("???",e);
		}
	}
	
	@Reference
	private HttpService httpService;
	
	@Activate
	public void start(final BundleContext ctx) {
		// http://stackoverflow.com/questions/1800317/impossible-to-make-a-cached-thread-pool-with-a-size-limit
		// https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
//		es = new ThreadPoolExecutor(2, 5, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
		es = ScalingThreadPoolExecutor.newScalingThreadPool(1, 5, 10 * 60 * 1000, new ThreadFactory() {
			
			private final AtomicInteger cnt = new AtomicInteger(0);
			
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				thread.setDaemon(true); // shall not keep the app alive
				thread.setName("Upnp_client_thread/pool_" + cnt.getAndIncrement());
				return thread;
			}
		});
		factory = DocumentBuilderFactory.newInstance();
		initThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				devices.clear();
				int failCounter = 0;
				try {
					InetAddress hanAddress = NetworkUtils.getHANAddress(logger);
					if (hanAddress == null)
						throw new RuntimeException("Local network interface could not be identified");
					InetSocketAddress isa = new InetSocketAddress(hanAddress, MULTICAST_PORT);
					multiSocket= new MulticastSocket(isa);
					multiSocket.setReuseAddress(true); // ?
				} catch (Exception e1) {
					failCounter++;
					if (failCounter > 15)
						failCounter = 15;
					logger.info("Network initialization failed",e1);
					try {
						Thread.sleep(failCounter^2*2000);
					} catch (InterruptedException e) {
						return;
					}
				}
		//		try {
		////			socket = new DatagramSocket(1900);
		//		} catch (IOException e1) {
		//			throw new RuntimeException("Server could not be initialized",e1);
		//		}
		//		listenOwn();
				listenMulti();
				failCounter = 0;
				while (failCounter >= 0) {
					try {
						sendDiscoveryRequest();
						failCounter = -1;
					} catch (Exception e) {
						failCounter++;
						logger.warn("Sending discovery request failed",e);
						try {
							Thread.sleep(failCounter^2 * 2000);
						} catch (InterruptedException e1) {
							return;
						}
					}
				}
				try {
					httpService.registerServlet("/org/smartrplace/drivers/upnp/overviewservlet", UpnpClient.this, null,null);
					httpService.registerResources(URL_BASE, "/org/smartrplace/upnp", null);
				} catch (ServletException | NamespaceException e) {
					throw new RuntimeException("Could not register servlet",e);
				}
				logger.info("{} started",getClass());
				int ownPort = 8443;
				String portProp = ctx.getProperty("org.osgi.service.http.port.secure");
				if (portProp != null) {
					try {
						ownPort = Integer.parseInt(portProp);
					} catch (Exception e) { /* ignore */ }
				}
				try {
					Browser.openPage("https://" + InetAddress.getLocalHost().getHostAddress() + ":" 
							+ ownPort + URL_BASE + "/index.html");
				} catch (UnknownHostException e) {
					logger.info("Opening Browser failed",e);
				}
			
			};
		});
		initThread.setName("SSDP_client_init_thread");
		initThread.start();
	}
	

	
	@Deactivate
	public void stop() {
		if (initThread!= null && initThread.isAlive())
			initThread.interrupt();
		initThread = null;
		try {
			httpService.unregister("/org/smartrplace/drivers/upnp/overviewservlet");
			httpService.unregister(URL_BASE);
		} catch (IllegalArgumentException e) { /* ignore */ }
		if (discoveryMulti != null) {
			discoveryMulti.interrupt();
		}
		discoveryMulti = null;
		if (discovery != null) {
			discovery.interrupt();
		}
		discovery = null;
		if (multiSocket != null) {
			multiSocket.disconnect();
			multiSocket.close();
		}
		multiSocket = null;
		if (es != null) {
			es.shutdownNow();
		}
		es = null;
		factory = null;
		devices.clear();
		logger.info("{}: bye bye",getClass());
	}
	
	private volatile boolean discoveryRunning = false;
	private volatile long lastUpdate = -1;
	
	void sendDiscoveryRequest() {
		synchronized (es) {
			if (discoveryRunning)
				return;
			long now = System.currentTimeMillis();
			if (now - lastUpdate < SCAN_TIMEOUT)
				return;
			discoveryRunning = true;
		}
		
		Runnable discover = new Runnable() {
			
			@Override
			public void run() {
		        byte[] receiveData = new byte[1024];
				byte[] bytes = MessageSearch.getDefaultSearchMessage().get().getBytes();
				DatagramPacket search = new DatagramPacket(bytes, bytes.length, MULTICAST_ADDRESS, MULTICAST_PORT);
				logger.debug("Sending device discovery request");
				try (DatagramSocket clientSocket = new DatagramSocket()) {
					clientSocket.setSoTimeout(SCAN_TIMEOUT);
			        clientSocket.send(search);
			        while (!Thread.interrupted()) { 
			        	DatagramPacket response = new DatagramPacket(receiveData, receiveData.length);
			        	clientSocket.receive(response);
			        	if (Thread.interrupted()) {
			        		Thread.currentThread().interrupt();
			        		break;
			        	}
			        	Runnable deviceCreation = new DeviceCreation(response, UpnpClient.this);
						es.submit(deviceCreation);
			        }
			    } catch (SocketTimeoutException e) { 
			    	/* timeout! */   
				} catch (IOException e) {
		        	logger.warn("Error in device discovery thread",e);
		        	return;
		        } finally {
		        	discoveryRunning = false;
		        	lastUpdate = System.currentTimeMillis();
		        }
				logger.debug("Discovery period finished");
			}
		};
		es.submit(discover);
	}
	
//	void listenOwn() {
//		ListenerTask task = new ListenerTask(socket);
//		discovery = new Thread(task);
//		discovery.setName("SSDP device discovery");
//		discovery.start();
//		logger.debug("Device discovery started at address {}:{}", socket.getLocalAddress().getHostAddress(), socket.getLocalPort());
//	}
	
	/**
	 * starts the update process, and returns immediately to the caller
	 * @param running
	 * 		set to false when done
	 */
	void listenMulti() {
		
		ListenerTask task = new ListenerTask(multiSocket, this) {
			public void init() {
				int failCounter = 0;
				try {
					multiSocket.joinGroup(MULTICAST_ADDRESS);
				} catch (Exception e) {
					failCounter++;
					logger.warn("Failed to connect to SSDP multicast thread",e);
					if (failCounter > 15)
						failCounter = 15;
					try {
						Thread.sleep(failCounter^2 * 2000);
					} catch (InterruptedException e1) {
						logger.info("Thread interrupted, stop trying to connect to SSDP multicast thread");
						Thread.currentThread().interrupt();
						return;
					}
				}
				logger.debug("UPNP client registered");
			};
		};
		discoveryMulti = new Thread(task);
		discoveryMulti.setName("SSDP multicast device discovery");
		discoveryMulti.start();
	}
	
	private static class ListenerTask implements Runnable {
		
		private final DatagramSocket socket;
		private final UpnpClient client;
		
		public ListenerTask(DatagramSocket socket, UpnpClient client) {
			this.socket = socket;
			this.client =client;
		}
		
		public void init() {}
		
		@Override
		public void run() {
			init();
			int	failCounter = 0;
			do {
				try {
					byte[] rxbuf = new byte[8192];
					DatagramPacket response = new DatagramPacket(rxbuf, rxbuf.length);
					socket.receive(response);
					if (Thread.interrupted())
						break;
					Runnable deviceCreation = new DeviceCreation(response, client);
					client.es.submit(deviceCreation);
					failCounter = 0;
				} catch (SocketException e) { //  when serverSocket.close() is called, and we are waiting in socket.receive()
					break;
				} catch (Exception e) {
					failCounter++;
					if (failCounter > 15)
						failCounter = 15;
					logger.debug("SSDP discovery thread failed; trying again",e); 
					try {
						Thread.sleep(failCounter^2*2000);
					} catch (InterruptedException e1) {
						break;
					}
				}
			} while (!Thread.interrupted());
			logger.info("Aborting SSDP device scan");
		}
		
	}
	
	private static class DeviceCreation implements Runnable {
		
		private final DatagramPacket response;
		private final UpnpClient client;
		
		public DeviceCreation(DatagramPacket response, UpnpClient client) {
			this.response = response;
			this.client = client;
		}

		@Override
		public void run() {
			RemoteDevice rd;
			try {
				String msg = new String(response.getData());
				if (msg.startsWith(MessageType.SEARCH.getIdentifier())) {
					logger.trace("Search request from {}:{}", response.getAddress().getHostAddress(), response.getPort());
					return;
				}
				Message message = Message.parse(msg);
				if (logger.isDebugEnabled())
					logger.debug("New message of type {} from {}:{}", message.getClass().getSimpleName(), response.getAddress().getHostAddress(), response.getPort());
				if (logger.isTraceEnabled()) 
					logger.trace("Message: " + msg);
				if (!message.rootDevice()) {
					logger.trace("Message not from root device, discarding...");
					return;
				}
				
//					if (!(message instanceof MessageNotify))
//						continue;
				if (message instanceof MessageNotify && ((MessageNotify) message).notifyType == NotifyType.BYE_BYE) {
					logger.debug("Device being removed: {}",((MessageNotify) message).uuid);
					client.devices.remove(((MessageNotify) message).uuid);
					return;
				}
				rd = new RemoteDevice(message);
			} catch (Exception e) {
				logger.warn("Could not create device from UDP response",e);
				return;
			}
			RemoteDevice oldDevice = client.devices.put(rd.uuid, rd);
			if (logger.isDebugEnabled()) {
				if (oldDevice == null) 
					logger.debug("New device {}", rd.uuid);
				else 
					logger.trace("New information about device {}", rd.uuid);
			}
			URL url = rd.location;
			try {
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				if (connection instanceof HttpsURLConnection) {
					HttpsURLConnection sslConn = (HttpsURLConnection) connection;
					sslConn.setHostnameVerifier(NOOP_VERIFIER);
					SSLContext ctx = SSLContext.getInstance("SSL");
					ctx.init(null,TRUST_ALL, new java.security.SecureRandom());
					sslConn.setSSLSocketFactory(ctx.getSocketFactory());
				}
				connection.setConnectTimeout(10000);
				InputStream is = connection.getInputStream();
				DocumentBuilder builder = client.factory.newDocumentBuilder();
				Document doc = builder.parse(is);
				rd.setDetails(doc);
			} catch (Exception e) {
				logger.error("Reading detailed information failed: " + rd.location,e); 
			}
			
		}
		
		
		
	}
	
	// avoid using a lib just for that...
	private static final HostnameVerifier NOOP_VERIFIER = new HostnameVerifier() {
		
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
		
	};
	
	private static final TrustManager[] TRUST_ALL = new TrustManager[] {
		new X509TrustManager() {
		
	        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
	            return null;
	        }
	        public void checkClientTrusted(X509Certificate[] certs, String authType) {
	        }
	        public void checkServerTrusted(X509Certificate[] certs, String authType) {
	        }
	    }
	};


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		sendDiscoveryRequest(); // update asynchronously
		JSONObject json = new JSONObject();
		for (Map.Entry<String, RemoteDevice> entry: devices.entrySet()) {
			RemoteDevice d = entry.getValue();
			if (!d.isValid())
				continue;
			json.put(entry.getKey(), d.getJSON());
		}
		resp.getWriter().write(json.toString());
		resp.setContentType("application/json");
		resp.setStatus(HttpServletResponse.SC_OK);
	}
	
	
	
}
