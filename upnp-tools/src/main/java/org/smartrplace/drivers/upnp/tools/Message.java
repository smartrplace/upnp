package org.smartrplace.drivers.upnp.tools;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.UUID;

public abstract class Message {
	
	public final static InetAddress MULTICAST_ADDRESS;
	public final static int MULTICAST_PORT = 1900;
	public final static String ROOT_DEVICE_IDENTIFIER = "upnp:rootdevice";
	public final MessageType type;
	public final static long BOOT_ID = System.currentTimeMillis();
	
	static {
		try {
			MULTICAST_ADDRESS = InetAddress.getByName("239.255.255.250");
		} catch (UnknownHostException e) {
			throw new RuntimeException("???");
		}
	}
	
	protected Message(MessageType type) {
		this.type = type;
	}
	
	protected abstract StringBuilder printFields();

	public String get() {
		return type.getIdentifier() + "\r\n"
			+  printFields().toString()
			+ "\r\n";
		
	}
	
	public static Message parse(byte[] message) {
		return parse(new String(message));
	}

	/**
	 * Specify whether this message contains information about the root device/requests
	 * information about the root device
	 * @return
	 */
	public abstract boolean rootDevice();
	
	public static Message parse(String message) throws IllegalArgumentException {
		if (message == null || message.trim().isEmpty())
			throw new IllegalArgumentException("Empty or null");
		String[] lines  = message.split("\r\n");
		MessageType type = MessageType.getType(lines[0]);
		if (type == null) 
			throw new IllegalArgumentException("Invalid reuqest: " + lines[0]);
		boolean hostOk = false;
		boolean manOk = false;
		int mx = -1;
		long maxAge = -1;
		String st = null;
		String userAgent = null;
		URL location = null;
		String nt = null;
		String nts = null;
		UUID uuid = null;
		long bootid = -1;
		long configId = -1;
		int searchport = -1;
		long nextBootId = -1;
		NotifyType nott = null;
		
		for (String line: lines) {
			if (line.isEmpty())
				continue;
			String[] entries = line.split(":", 2);
 			if (entries.length != 2)
 				continue;
			String key = entries[0].trim().toUpperCase();
			String value = entries[1].trim();
			switch(key) {
			case "HOST":
				if (!value.equals(MULTICAST_ADDRESS.getHostAddress() + ":" + MULTICAST_PORT))
					throw new IllegalArgumentException("Invalid host " + value);
				else
					hostOk = true;
				continue;
			case "MAN":
				if (!value.equals("\"ssdp:discover\""))
					throw new IllegalArgumentException("Invalid MAN header " + value);
				else
					manOk = true;
				continue;
			case "MX": 
				try {
					mx = Integer.parseInt(value);
					if (mx <=0) 
						throw new NumberFormatException();
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid MX header " + value);
				}
				continue;
			case "ST": 
				st  = value;
				continue;
			case "USER-AGENT":
				userAgent = value;
				continue;
			case "CACHE-CONTROL":
				String[] v = value.split("=");
				if (v.length != 2 || !v[0].trim().equals("max-age"))
					throw new IllegalArgumentException("illegal CACHE-CONTROL " + value);
				try {
					maxAge = Long.parseLong(v[1].trim());
					if (maxAge <=0) 
						throw new NumberFormatException();
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid CACHE-CONTROL header " + value);
				}
				continue;
			case "LOCATION":
				try {
					location = new URL(value);
				} catch (MalformedURLException e) {
					throw new IllegalArgumentException("Illegal LOCATION header, must be a valid URL: " + value);				}
				continue;
			case "NT":
				nt = value;
				continue;
			case "NTS":
				v = value.split(":");
				if (v.length != 2 || !v[0].trim().equals("ssdp"))
					throw new IllegalArgumentException("Illegal NTS header " + value);
				nott = NotifyType.getType(v[1].trim());
				if (nott == null)
					throw new IllegalArgumentException("Illegal NTS header " + value);
				continue;
			case "SERVER":
				userAgent = value;
				continue;
			case "USN":
				String[] arr = value.split(":");
				if (arr.length < 2)
					throw new IllegalArgumentException("Illegal USN header field: " + value);
				uuid = UUID.fromString(arr[1]); // throws IllegalArgumentException
				continue;
			case "BOOTID.UPNP.ORG":
				try {
					bootid = Long.parseLong(value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Illegal BOOTID header " +value);
				}
				continue;
			case "CONFIGID.UPNP.ORG":
				try {
					configId = Long.parseLong(value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Illegal CONFIGID header " +value);
				}
				continue;
			case "SEARCHPORT.UPNP.ORG":
				try {
					searchport = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Illegal SEARCHPORT header " +value);
				}
				continue;
			case "NEXTBOOTID.UPNP.ORG":
				try {
					nextBootId = Long.parseLong(value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Illegal NEXTBOOTID header " +value);
				}
				continue;
			default:
				// ignore
				continue;
			}
			
		}
		switch (type) {
		case SEARCH:
			if (!hostOk)
				throw new IllegalArgumentException("HOST missing");
			return new MessageSearch(mx, st);
		case HTTP:
			return new MessageResponse(uuid.toString(), location, maxAge, configId, st, userAgent);
		case NOTIFY:
			if (!hostOk)
				throw new IllegalArgumentException("HOST missing");
			if (nott == null)
				throw new IllegalArgumentException("NT header missing");
			return new MessageNotify(uuid.toString(), nott, location, maxAge, configId, nt, userAgent);
		default:
			throw new IllegalArgumentException("Type not found");
		}
		
	}

}
