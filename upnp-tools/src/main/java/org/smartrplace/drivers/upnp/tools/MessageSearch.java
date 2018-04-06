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

import java.lang.ref.SoftReference;
import java.util.Objects;

/**
 * Used to reply to search requests 
 *
 */
public class MessageSearch extends Message {
	/**
	 * in s
	 */
	public final int maxDelay; 
	public final String st;
	
	public MessageSearch() {
		this(3, ROOT_DEVICE_IDENTIFIER);
	}
	
	public MessageSearch(int maxDelay, String st) {
		super(MessageType.SEARCH);
		if (maxDelay <= 0 || maxDelay > 5) 
			throw new IllegalArgumentException("max delay must be between 0 and 5");
		Objects.requireNonNull(st);
		this.maxDelay = maxDelay;
		this.st = st;
	}

	@Override
	protected StringBuilder printFields() {
		StringBuilder sb = new StringBuilder();
		sb.append("HOST: " + MULTICAST_ADDRESS.getHostAddress() + ":" + MULTICAST_PORT + "\r\n");
		sb.append("ST: " + st + "\r\n");
		sb.append("MAN: \"ssdp:discover\"\r\n");
		sb.append("MX: " + maxDelay + "\r\n");
		return sb;
	}
	
	@Override
	public boolean rootDevice() {
		return st.equals(ROOT_DEVICE_IDENTIFIER);
	}
	
	private static volatile SoftReference<MessageSearch> defaultMessage = null;
	
	public static final MessageSearch getDefaultSearchMessage() {
		MessageSearch search = null;
		if (defaultMessage != null) {
			search = defaultMessage.get();
		}
		if (search == null) {
			search = new MessageSearch();
			defaultMessage = new SoftReference<MessageSearch>(search);
		}
		return search;
	}
	
}
