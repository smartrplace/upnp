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
