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

// Client di prova che invia una richiesta PUT con payload testuale a un endpoint CoAP
public class CoapPutClientProcess {

    private static final Logger logger = LoggerFactory.getLogger(CoapPutClientProcess.class);

    // Esegue la richiesta PUT includendo un payload opzionale fornito da linea di comando
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CoapPutClientProcess [URI] [Payload]");
            System.exit(-1);
        }

        String uri = args[0];
        String payload = (args.length > 1) ? args[1] : "";

        CoapClient coapClient = new CoapClient(uri);

        try {
            logger.info("Sending PUT request to: {} with payload: {}", uri, payload);
            Request request = new Request(CoAP.Code.PUT);
            request.setPayload(payload);
            request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);

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