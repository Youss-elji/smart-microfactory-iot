package it.unimore.iot.microfactory.coap.client;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CoapPostClientProcess {

    private static final Logger logger = LoggerFactory.getLogger(CoapPostClientProcess.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: CoapPostClientProcess [URI]");
            System.exit(-1);
        }

        String uri = args[0];
        CoapClient coapClient = new CoapClient(uri);

        try {
            logger.info("Sending POST request to: {}", uri);
            Request request = new Request(CoAP.Code.POST);
            // In a real scenario, you might add a payload, e.g., request.setPayload(...)

            CoapResponse coapResponse = coapClient.advanced(request);

            if (coapResponse != null) {
                logger.info("Response Code: {}", coapResponse.getCode());
                if (coapResponse.isSuccess()) {
                    logger.info("Payload: {}", coapResponse.getResponseText());
                }
            } else {
                logger.error("Request failed");
            }

        } catch (ConnectorException | IOException e) {
            e.printStackTrace();
        } finally {
            coapClient.shutdown();
        }
    }
}