package org.smartrplace.drivers.upnp.provider.impl;

class Properties {

	static final String BOX_NAME = System.getProperty("org.smartrplace.upnp.provider.gateway", "Smartrplace Box");
	static final String START_PAGE = System.getProperty("org.smartrplace.upnp.provider.startpage","/ogema/index.html");
	static final String MANUFACTURER = System.getProperty("org.smartrplace.upnp.provider.manufacturer", "Smartrplace UG");
	static final String URL = System.getProperty("org.smartrplace.upnp.provider.url", "http://www.smartrplace.de");
	static final String MODEL_URL = System.getProperty("org.smartrplace.upnp.provider.model_url", "http://www.smartrplace.de");
	static final String MODEL_NAME = System.getProperty("org.smartrplace.upnp.provider.model_name", BOX_NAME);
	static final String MODEL_DESCRIPTION = System.getProperty("org.smartrplace.upnp.provider.model_description", "Smartrplace Smart Home gateway");
	static final String MODEL_NUMBER = System.getProperty("org.smartrplace.upnp.provider.model_number", "0");
	static final String UPC = System.getProperty("org.smartrplace.upnp.provider.upc", "SMPL0001");
	
	static final long UPDATE_INTERVAL = Long.getLong("org.smartrplace.upnp.provider.update_interval_ms", 300000);
	
	
}
