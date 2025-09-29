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

public class CoapAutomaticClient {

    private static final Logger logger = LoggerFactory.getLogger(CoapAutomaticClient.class);
    private static final String COAP_SERVER_URI = "coap://localhost:5683";

    private static final String SENSOR_RT = "it.unimore.device.sensor.capsule";
    private static final String ACTUATOR_RT = "it.unimore.device.actuator.task";

    public static void main(String[] args) {
        try {
            // Discover resources
            String targetUri = String.format("%s/.well-known/core", COAP_SERVER_URI);
            CoapClient discoveryClient = new CoapClient(targetUri);
            CoapResponse response = discoveryClient.get(ContentFormat.APPLICATION_LINK_FORMAT);

            if (response == null || !response.isSuccess()) {
                logger.error("Discovery failed! Response: {}", response);
                return;
            }

            logger.info("Discovery successful. Found resources:");
            Set<WebLink> links = LinkFormat.parse(response.getResponseText());
            links.forEach(link -> logger.info("-> {}", link.getURI()));

            // Find sensor and actuator
            String sensorUri = findResourceUri(links, SENSOR_RT);
            String actuatorUri = findResourceUri(links, ACTUATOR_RT);

            if (sensorUri == null || actuatorUri == null) {
                logger.error("Could not find required sensor ({}) or actuator ({}) resources.", SENSOR_RT, ACTUATOR_RT);
                return;
            }

            logger.info("Found capsule sensor at: {}", sensorUri);
            logger.info("Found task actuator at: {}", actuatorUri);

            // Get sensor state in SenML+JSON
            CoapClient sensorClient = new CoapClient(COAP_SERVER_URI + sensorUri);
            Request getRequest = new Request(CoAP.Code.GET);
            getRequest.getOptions().setAccept(ContentFormat.APPLICATION_SENML_JSON);
            CoapResponse sensorResponse = sensorClient.advanced(getRequest);

            if (sensorResponse != null && sensorResponse.isSuccess()) {
                logger.info("Sensor state (SenML): {}", sensorResponse.getResponseText());
                // Simple logic: if sensor reports something (e.g., capsule present), activate actuator
                // A real implementation would parse the SenML response.
                if (sensorResponse.getResponseText().contains("\"vb\":true") || sensorResponse.getResponseText().contains("\"v\":1")) {
                    logger.info("Capsule detected! Triggering actuator...");

                    // Activate actuator
                    CoapClient actuatorClient = new CoapClient(COAP_SERVER_URI + actuatorUri);
                    CoapResponse actuatorResponse = actuatorClient.post("start", MediaTypeRegistry.TEXT_PLAIN);

                    if (actuatorResponse != null && actuatorResponse.isSuccess()) {
                        logger.info("Actuator triggered successfully! Response: {}", actuatorResponse.getResponseText());
                    } else {
                        logger.error("Failed to trigger actuator. Response: {}", actuatorResponse);
                    }
                    actuatorClient.shutdown();
                } else {
                    logger.info("Condition not met, not triggering actuator.");
                }
            } else {
                logger.error("Failed to get sensor state. Response: {}", sensorResponse);
            }
            sensorClient.shutdown();

        } catch (ConnectorException | IOException e) {
            e.printStackTrace();
        }
    }

    private static String findResourceUri(Set<WebLink> links, String resourceType) {
        return links.stream()
                .filter(link -> link.getAttributes().getResourceTypes().contains(resourceType))
                .map(WebLink::getURI)
                .findFirst()
                .orElse(null);
    }
}