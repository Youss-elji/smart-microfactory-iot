package it.unimore.iot.microfactory.util;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;

public class TestCoapManual {

    public static void main(String[] args) {
        String uri = "coap://localhost:5683/.well-known/core";
        CoapClient client = new CoapClient(uri);

        try {
            CoapResponse resp = client.get();
            if (resp != null) {
                System.out.println("Code: " + resp.getCode());
                System.out.println("Text: " + resp.getResponseText());
            } else {
                System.out.println("No response received.");
            }
        } catch (Exception e) {
            System.err.println("Errore durante la richiesta CoAP: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}
