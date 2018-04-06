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

/**
 * Used to reply to search requests 
 *
 */
public class MessageResponse extends Message {

	public final URL location;
	public final long seconds;
	public final long configId;
	public final String uuid;
	public final String st;
	public final String server;
	
	public MessageResponse(String uuid, URL location, long seconds, long configId) {
		this(uuid, location, seconds, configId, ROOT_DEVICE_IDENTIFIER, "unix/8 UPnP/1.1 OGEMA/2.1");
	}
	
	public MessageResponse(String uuid, URL location, long seconds, long configId, String st, String server) {
		super(MessageType.HTTP);
		Objects.requireNonNull(location);
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(st);
		this.configId = configId;
		this.seconds = seconds;
		this.uuid = uuid;
		this.location = location;
		this.st = st;
		this.server = server;
	}
	
	@Override
	public boolean rootDevice() {
		return st.equals(ROOT_DEVICE_IDENTIFIER);
	}

	@Override
	protected StringBuilder printFields() {
		StringBuilder sb = new StringBuilder();
		sb.append("CACHE-CONTROL:max-age=" + seconds + "\r\n"); // TODO DATE
		sb.append("EXT:\r\n");
		sb.append(
			  "LOCATION: " + location.toString() + "\r\n"
			+ "ST: " + st +"\r\n"
			+ "SERVER: " +server + "\r\n"
			+ "USN: uuid:" + uuid + "::upnp:rootdevice\r\n"  
			+ "BOOTID.UPNP.ORG: " + BOOT_ID + "\r\n"
			+ "CONFIGID.UPNP.ORG: " + configId + "\r\n");
		return sb;
	}
	
}
