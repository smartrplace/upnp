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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import org.json.JSONObject;
import org.smartrplace.drivers.upnp.tools.Message;
import org.smartrplace.drivers.upnp.tools.MessageNotify;
import org.smartrplace.drivers.upnp.tools.MessageResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class RemoteDevice {
	
	// note: while address and port are final, they can theoretically change for a given device, which is why we update 
	// RemoteDevice instances in the global map, every time new information from some device is available
//	public final InetAddress address;
//	public final int port;
	public final URL location;
	public final long maxAgeSeconds;
	public final long lastUpdate;
	public final String uuid;
	public final String device;
//	public final URL gatewayLink;
	private volatile Document detailInformation;
	
	RemoteDevice(Message message) {
		Objects.requireNonNull(message);
		lastUpdate = System.currentTimeMillis();
		if (message instanceof MessageNotify) {
			MessageNotify msg = (MessageNotify) message;
			location = msg.location;
			uuid = msg.uuid;
			maxAgeSeconds = msg.seconds;
			device = msg.server;
		}
		else if (message instanceof MessageResponse) {
			MessageResponse msg =(MessageResponse) message;
			location = msg.location;
			uuid = msg.uuid;
			maxAgeSeconds = msg.seconds;
			device = msg.server;
		}
		else 
			throw new IllegalArgumentException("Illegal message type: " + message);
//		URL aux = null;
//		if (isOgemaGateway(device)) {
//			String url = location.getProtocol() + "://" + location.getHost() + ":" + location.getPort() + "/ogema/index.html";
//			try {
//				aux = new URL(url);
//			} catch (MalformedURLException e) {
//			}
//		}
//		gatewayLink = aux;
	}
	
	private static final boolean isOgemaGateway(String device) {
		return device.toLowerCase().contains("ogema");
	}
	
	public boolean isValid() {
		return System.currentTimeMillis() < (lastUpdate + maxAgeSeconds * 1000); 
	}
	
	@Override
	public String toString() {
		return "Device at " + location.toString() + "\n   UUID: " + uuid;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof RemoteDevice))
			return false;
		RemoteDevice other = (RemoteDevice) obj;
		return this.uuid.equals(other.uuid);
	}
	
	@Override
	public int hashCode() {
		return uuid.hashCode();
	}

	public JSONObject getJSON() {
		JSONObject json = new JSONObject();
		json.put("location", location.toString());
		json.put("uuid", uuid);
		json.put("lifetime", (lastUpdate + maxAgeSeconds*1000 - System.currentTimeMillis()) / 1000 );
		json.put("device", device);
//		if (gatewayLink != null)
//			json.put("gatewayLink", gatewayLink);
		String url  =getDevicePresentationAddress(); // note: this can be a relative URL or a full URL!
		if (url != null) {
			url = createPath(location, url);
			json.put("gatewayLink", url);
		}
		String friendlyName = getFriendlyName();
		if (friendlyName == null)
			friendlyName = uuid; // not so friendly...
		json.put("friendlyName", friendlyName);
		String iconPath = getIconPath();
		if (iconPath != null) {
			iconPath = createPath(location, iconPath);
			json.put("icon", iconPath);
		}
		return json;
	}

	private static String createPath(URL location, String url) {
		try {
			url = new URL(url).toString(); // check whether it is a valid URL, and make sure the check is not optimized away
		} catch (MalformedURLException e) {
			if (url.startsWith("/"))
				url = location.getProtocol() + "://" + location.getHost() + ":" + location.getPort() + url;
			else {
				String path = location.getPath();
				if (path != null && !path.isEmpty()) {
					int idx = path.lastIndexOf('/');
					if (idx > 0)
						path = path.substring(0, idx+1);
					else
						path = "/";
				}
				if (!path.endsWith("/"))
					path += "/";
				url = location.getProtocol() + "://" + location.getHost() + ":" + location.getPort() 
						+ path + url;
			}
		}
		return url;
	}
	
	void setDetails(Document doc) {
		this.detailInformation = doc;
	}
	
	public String getDevicePresentationAddress()  {
		return getTag("presentationURL", detailInformation);
	}
	
	public String getFriendlyName() {
		return getTag("friendlyName", detailInformation);
	}
	
	public String getIconPath() {
		final Document detailInformation = this.detailInformation;
		if (detailInformation ==null)
			return null;
		NodeList nl = detailInformation.getElementsByTagName("icon");
		int sz = nl.getLength();
		if (sz == 0)
			return null;
		int smallestSize = Integer.MAX_VALUE;
		String bestUrl = null;
		for (int i=0;i<sz;i++) {
			Node n = nl.item(i);
			NodeList subs = n.getChildNodes();
			int currentSize = Integer.MAX_VALUE;
			String url = null;
			for (int j=0;j<subs.getLength();j++) {
				Node s = subs.item(j);
				String name = s.getNodeName();
				if (name == null)
					continue;
				switch (name.toLowerCase()) {
				case "height":
					try {
						currentSize = Integer.parseInt(s.getTextContent());
					} catch (Exception e) {
						continue;
					}
					break;
				case "url": 
					url = s.getTextContent();
					break;
				default:
					break;
				}
			}
			if (currentSize < smallestSize && url != null) {
				smallestSize = currentSize;
				bestUrl = url;
			}
			
		}
		return bestUrl;
		
	}
	
	private static String getTag(String tagName, Document doc) {
		if (doc ==null)
			return null;
		NodeList nl = doc.getElementsByTagName(tagName);
		if (nl.getLength() == 0)
			return null;
		Node n = nl.item(0);
		return n.getTextContent();
	}
	
}
