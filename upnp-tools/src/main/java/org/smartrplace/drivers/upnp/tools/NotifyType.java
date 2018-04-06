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

public enum NotifyType {

	AVAILABLE("alive"), BYE_BYE("byebye"), UPDATE("update");
	
	private final String ssdp;
	
	private NotifyType(String ssdp) {
		this.ssdp = ssdp;
	}
	
	public String getType() {
		return ssdp;
	}

	public static NotifyType getType(String identifier) {
		for (NotifyType t : NotifyType.values()) {
			if (t.ssdp.equals(identifier))
				return t;
		}
		return null;
	}
	
}
