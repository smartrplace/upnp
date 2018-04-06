package org.smartrplace.drivers.upnp.provider.impl;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class XmlDescriptorServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final UpnpProvider provider;
	private final SoftReference<char[]> response = new SoftReference<char[]>(null);
	private volatile long lastUpdate = -1;
	
	XmlDescriptorServlet(UpnpProvider provider) {
		this.provider = provider;
	}
	
	private char[] getResponse() throws UnknownHostException, SocketException {
		final boolean cacheExpired = System.currentTimeMillis() - lastUpdate > Properties.UPDATE_INTERVAL;
		char[] text = cacheExpired ? null : response.get();
		if (text != null)
			return text;
		synchronized (response) {
			text = response.get();
			if (text != null)
				return text;
			final StringBuilder sb=  new StringBuilder();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			sb.append("<root xmlns=\"urn:schemas-upnp-org:device-1-0\">")
				.append("<specVersion><major>1</major><minor>1</minor></specVersion>")
				.append("<device>")
					.append("<deviceType>urn:schemas-upnp-org:device:Basic:1.0</deviceType>")
					.append("<friendlyName>").append(Properties.BOX_NAME).append("</friendlyName>")
					.append("<manufacturer>").append(Properties.MANUFACTURER).append("</manufacturer><manufacturerURL>")
							.append(Properties.URL).append("</manufacturerURL><modelDescription>")
							.append(Properties.MODEL_DESCRIPTION).append("</modelDescription><modelURL>")
							.append(Properties.MODEL_URL).append("</modelURL><modelName>")
							.append(Properties.MODEL_NAME).append("</modelName><modelNumber>")
							.append(Properties.MODEL_NUMBER).append("</modelNumber>")
					.append("<UDN>uuid:").append(provider.uuid).append("</UDN>")
					.append("<UPC>").append(Properties.UPC).append("</UPC><serialNumber>").append(provider.uuid).append("</serialNumber>")
					.append("<iconList>")
						.append("<icon>")
							.append("<mimetype>image/png</mimetype>")
							.append("<height>16</height><width>16</width><depth>16</depth><url>favicon.png</url>")
						.append("</icon>")
					.append("</iconList>")
					.append("<serviceList></serviceList>")
					.append("<presentationURL>").append(UpnpProvider.getBaseUrl()).append(Properties.START_PAGE).append("</presentationURL>")
				.append("</device>")
			.append("</root>");
			text = new char[sb.length()];
			sb.getChars(0, sb.length(), text, 0);
			lastUpdate = System.currentTimeMillis();
			return text;
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/xml");
		resp.getWriter().write(getResponse());
		resp.setStatus(HttpServletResponse.SC_OK);
	}
	
}
