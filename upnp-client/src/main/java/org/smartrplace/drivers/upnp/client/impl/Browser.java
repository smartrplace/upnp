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
