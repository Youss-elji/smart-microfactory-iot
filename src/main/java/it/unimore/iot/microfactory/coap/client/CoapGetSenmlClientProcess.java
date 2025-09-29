package it.unimore.iot.microfactory.coap.client;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CoapGetSenmlClientProcess {

    private static final Logger logger = LoggerFactory.getLogger(CoapGetSenmlClientProcess.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: CoapGetSenmlClientProcess [URI]");
            System.exit(-1);
        }

        String uri = args[0];
        CoapClient coapClient = new CoapClient(uri);

        try {
            logger.info("Sending GET request to: {}", uri);
            Request request = new Request(CoAP.Code.GET);
            request.getOptions().setAccept(MediaTypeRegistry.APPLICATION_SENML_JSON);

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