package org.smartrplace.drivers.upnp.tools;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.slf4j.Logger;

public class NetworkUtils {
	
	public static InetAddress getHANAddress(Logger logger) throws SocketException, UnknownHostException {
		InetAddress ownAddress = InetAddress.getLocalHost();
		if (logger.isTraceEnabled())
			logger.trace("Checking own address {}", ownAddress.getHostAddress());
		if (!ownAddress.isLoopbackAddress()) {
			logger.debug("Address determined: {}", ownAddress);
			return ownAddress;
		}
		Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
		while (nifs.hasMoreElements()) {
			NetworkInterface nif = nifs.nextElement();
			if (logger.isTraceEnabled())
				logger.trace("Checking network interface {}", nif.getName());
			if (nif.isLoopback() || nif.isVirtual())
				continue;
			Enumeration<InetAddress> addresses = nif.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress ia = addresses.nextElement();
				if (logger.isTraceEnabled())
					logger.trace("Checking own address {}", ia.getHostAddress());
				String host = ia.getHostAddress();
				if (host.startsWith("192.168") || host.startsWith("10.")) {
					logger.debug("Address found: {}",ia);
					return ia;
				}
				if (host.startsWith("172.")) {
					try {
						String[] cmp = host.split("\\.");
						int second = Integer.parseInt(cmp[1]);
						if (16 <= second && 31 >= second) {
							logger.debug("Address found: {}",ia);
							return ia;
						}
					} catch (Exception e) { 
						continue;
					}
				}
			}
		}
		return null;
	}

}
