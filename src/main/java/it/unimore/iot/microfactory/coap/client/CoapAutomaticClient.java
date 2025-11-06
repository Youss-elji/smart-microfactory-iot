package it.unimore.iot.microfactory.coap.client;

import it.unimore.iot.microfactory.util.coap.ContentFormat;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

// Client dimostrativo che scopre risorse CoAP e attiva automaticamente un attuatore in base a un sensore
public class CoapAutomaticClient {

    private static final Logger logger = LoggerFactory.getLogger(CoapAutomaticClient.class);
    private static final String COAP_SERVER_URI = "coap://localhost:5683";

    private static final String SENSOR_RT = "it.unimore.device.sensor.capsule";
    private static final String ACTUATOR_RT = "it.unimore.device.actuator.task";

    public static void main(String[] args) {
        try {
            // Esegue la discovery delle risorse esposte dal server CoAP
            String targetUri = String.format("%s/.well-known/core", COAP_SERVER_URI);
            CoapClient discoveryClient = new CoapClient(targetUri);
            CoapResponse response = discoveryClient.get(ContentFormat.APPLICATION_LINK_FORMAT);

            if (response == null || !response.isSuccess()) {
                logger.error("Discovery failed! Response: {}", response);
                return;
            }

            logger.info("Discovery completata. Risorse disponibili:");
            Set<WebLink> links = LinkFormat.parse(response.getResponseText());
            links.forEach(link -> logger.info("-> {}", link.getURI()));

            // Ricerca delle risorse di sensore e attuatore nella lista scoperta
            String sensorUri = findResourceUri(links, SENSOR_RT);
            String actuatorUri = findResourceUri(links, ACTUATOR_RT);

            if (sensorUri == null || actuatorUri == null) {
                logger.error("Could not find required sensor ({}) or actuator ({}) resources.", SENSOR_RT, ACTUATOR_RT);
                return;
            }

            logger.info("Sensore capsula individuato: {}", sensorUri);
            logger.info("Attuatore task individuato: {}", actuatorUri);

            // Recupera lo stato del sensore in formato SenML+JSON
            CoapClient sensorClient = new CoapClient(COAP_SERVER_URI + sensorUri);
            Request getRequest = new Request(CoAP.Code.GET);
            getRequest.getOptions().setAccept(ContentFormat.APPLICATION_SENML_JSON);
            CoapResponse sensorResponse = sensorClient.advanced(getRequest);

            if (sensorResponse != null && sensorResponse.isSuccess()) {
                logger.info("Sensor state (SenML): {}", sensorResponse.getResponseText());
                // Logica semplificata: se il sensore segnala presenza capsula, attiva l'attuatore
                // In produzione si dovrebbe analizzare il payload SenML anziché cercare stringhe
                if (sensorResponse.getResponseText().contains("\"vb\":true") || sensorResponse.getResponseText().contains("\"v\":1")) {
                    logger.info("Capsula rilevata! Avvio dell'attuatore...");

                    // Attiva l'attuatore inviando un comando POST testuale
                    CoapClient actuatorClient = new CoapClient(COAP_SERVER_URI + actuatorUri);
                    CoapResponse actuatorResponse = actuatorClient.post("start", MediaTypeRegistry.TEXT_PLAIN);

                    if (actuatorResponse != null && actuatorResponse.isSuccess()) {
                        logger.info("Actuator triggered successfully! Response: {}", actuatorResponse.getResponseText());
                    } else {
                        logger.error("Failed to trigger actuator. Response: {}", actuatorResponse);
                    }
                    actuatorClient.shutdown();
                } else {
                    logger.info("Condizione non soddisfatta, attuatore non attivato.");
                }
            } else {
                logger.error("Failed to get sensor state. Response: {}", sensorResponse);
            }
            sensorClient.shutdown();

        } catch (ConnectorException | IOException e) {
            e.printStackTrace();
        }
    }

    // Ricerca l'URI della risorsa con il resource type richiesto fra quelli scoperti
    private static String findResourceUri(Set<WebLink> links, String resourceType) {
        return links.stream()
                .filter(link -> link.getAttributes().getResourceTypes().contains(resourceType))
                .map(WebLink::getURI)
                .findFirst()
                .orElse(null);
    }
}