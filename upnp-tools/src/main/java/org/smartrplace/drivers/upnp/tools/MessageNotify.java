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
package org.smartrplace.drivers.upnp.tools;

import java.net.URL;
import java.util.Objects;

public class MessageNotify extends Message {

	public final NotifyType notifyType;
	public final long seconds;
	public final URL location;
	public final long configId;
	public final String uuid;
	public final String nt;
	public final String server;
	
	public MessageNotify(String uuid, NotifyType notifyType, URL location, long seconds, long configId) {
		this(uuid, notifyType, location, seconds, configId, ROOT_DEVICE_IDENTIFIER, "unix/8 UPnP/1.1 OGEMA/2.1");
	}
	
	public MessageNotify(String uuid, NotifyType notifyType, URL location, long seconds, long configId, String nt, String server) {
		super(MessageType.NOTIFY);
		if (seconds <= 0 && notifyType != NotifyType.BYE_BYE)
			throw new IllegalArgumentException("seconds must be > 0");
		Objects.requireNonNull(location);
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(nt);
		this.notifyType = notifyType;
		this.seconds = seconds;
		this.configId = configId;
		this.uuid = uuid;
		this.location = location;
		this.nt = nt;
		this.server = server;
	}
	
	@Override
	public boolean rootDevice() {
		return nt.equals(ROOT_DEVICE_IDENTIFIER);
	}

	@Override
	protected StringBuilder printFields() {
		StringBuilder sb = new StringBuilder();
		sb.append("HOST: " + MULTICAST_ADDRESS.getHostAddress() + ":" + MULTICAST_PORT + "\r\n");
		if (notifyType == NotifyType.AVAILABLE) 
			sb.append("CACHE-CONTROL:max-age=" + seconds + "\r\n");
		sb.append(
			  "LOCATION: " + location.toString() + "\r\n"
			+ "NT: " + nt +"\r\n"
			+ "NTS: ssdp:" + notifyType.getType() + "\r\n"
			+ "SERVER: " + server +"\r\n"
			+ "USN: uuid:" + uuid + "::upnp:rootdevice\r\n" 
			+ "BOOTID.UPNP.ORG: " + BOOT_ID + "\r\n"
			+ "CONFIGID.UPNP.ORG: " + configId + "\r\n");
		if (notifyType == NotifyType.UPDATE)
			sb.append("NEXTBOOTID.UPNP.ORG: " + 1 + "\r\n"); // TODO
		return sb;
	}
	
}
