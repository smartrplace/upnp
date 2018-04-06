package org.smartrplace.drivers.upnp.tools;

public enum MessageType {
	
	SEARCH("M-SEARCH * HTTP/1.1"), NOTIFY("NOTIFY * HTTP/1.1"), HTTP("HTTP/1.1 200 OK");
	
	private final String identifier;
	
	MessageType(String identifier) {
		this.identifier = identifier;
	}
	
	public String getIdentifier() {
		return identifier; 
	}
	
	public static MessageType getType(String identifier) {
		for (MessageType t : MessageType.values()) {
			if (t.identifier.equals(identifier))
				return t;
		}
		return null;
	}

}
