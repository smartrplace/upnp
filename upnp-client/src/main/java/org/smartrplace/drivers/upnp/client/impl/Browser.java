package org.smartrplace.drivers.upnp.client.impl;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

class Browser {

	// http://stackoverflow.com/questions/5226212/how-to-open-the-default-webbrowser-using-java
	static boolean openPage(String url) {
		if (Desktop.isDesktopSupported()) {
			Desktop desktop = Desktop.getDesktop();
			try {
				desktop.browse(new URI(url));
				return true;
			} catch (Exception e) {
				UpnpClient.logger.error("Failed to open Browser",e);
//				return false;
			}
		}
		String os = System.getProperty("os.name").toLowerCase();
		Runtime rt = Runtime.getRuntime();
		Process pr;
		if (os.indexOf( "nix") >=0 || os.indexOf( "nux") >=0) {
			String[] browsers = {"epiphany", "firefox", "mozilla", "konqueror",
			                                 "netscape","opera","links","lynx"};

			StringBuffer cmd = new StringBuffer();
			for (int i=0; i<browsers.length; i++)
			     cmd.append( (i==0  ? "" : " || " ) + browsers[i] +" \"" + url + "\" ");

			try {
				pr = rt.exec(new String[] { "sh", "-c", cmd.toString() });
				return true;
			} catch (IOException e) {
				UpnpClient.logger.error("Failed to open Browser",e);
				return false;
			}
		}
		else if(os.indexOf( "mac" ) >= 0) {
			try {
				pr = rt.exec( "open" + url);
			} catch (IOException e) {
				UpnpClient.logger.error("Failed to open Browser",e);
				return false;
			}
			
		}
		else if (os.indexOf( "win" ) > 0) {
			try {
				pr = rt.exec( "rundll32 url.dll,FileProtocolHandler " + url);
			} catch (IOException e) {
				UpnpClient.logger.error("Failed to open Browser",e);
				return false;
			}
		}
		else 
			return false;
		return true;
	}
	
}
