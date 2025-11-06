package it.unimore.iot.microfactory.util.coap;

import org.eclipse.californium.core.coap.MediaTypeRegistry;

// Wrapper che espone i content format CoAP utilizzati nel progetto
public class ContentFormat {
    public static final int APPLICATION_SENML_JSON = MediaTypeRegistry.APPLICATION_SENML_JSON;
    public static final int TEXT_PLAIN = MediaTypeRegistry.TEXT_PLAIN;
    public static final int APPLICATION_JSON = MediaTypeRegistry.APPLICATION_JSON;
    public static final int APPLICATION_LINK_FORMAT = MediaTypeRegistry.APPLICATION_LINK_FORMAT;
}