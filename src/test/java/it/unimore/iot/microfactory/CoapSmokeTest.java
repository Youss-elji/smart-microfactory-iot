package it.unimore.iot.microfactory;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoapSmokeTest {

    @Test
    void wellKnownCore_shouldRespond() {
        final String uri = "coap://localhost:5683/.well-known/core";

        // 1) Config SENZA file (evita “Configuration contains no definitions/values”)
        Configuration cfg = Configuration.createStandardWithoutFile();

        // 2) Endpoint esplicito con quella config
        CoapEndpoint endpoint = new CoapEndpoint.Builder()
                .setConfiguration(cfg)
                .build();

        // 3) Client con endpoint dedicato
        CoapClient client = new CoapClient(uri);
        client.setEndpoint(endpoint);

        try {
            CoapResponse resp = null;
            try {
                resp = client.get();
            } catch (ConnectorException e) {
                throw new RuntimeException( e );
            } catch (IOException e) {
                throw new RuntimeException( e );
            }

            // Se il server non è avviato, skippo il test invece di farlo fallire
            Assumptions.assumeTrue(resp != null, "CoAP server non raggiungibile su " + uri);

            System.out.println("CoAP /.well-known/core -> " + resp.getCode());
            System.out.println(resp.getResponseText());
            assertTrue(resp.isSuccess(), "Risposta CoAP non di successo");
        } finally {
            client.shutdown();
        }
    }
}
