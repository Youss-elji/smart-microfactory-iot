package it.unimore.iot.microfactory.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class MqttClientManager {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientManager.class);

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID_PREFIX = "iot-device-";

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;

    public MqttClientManager(String deviceId) throws MqttException {
        String clientId = CLIENT_ID_PREFIX + deviceId;
        MqttClientPersistence persistence = new MemoryPersistence();
        this.mqttClient = new MqttClient(BROKER_URL, clientId, persistence);
        this.objectMapper = new ObjectMapper();
    }

    public void connect() throws MqttException {
        if (!this.mqttClient.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            this.mqttClient.connect(options);
            logger.info("MQTT Client connected to broker: {}", BROKER_URL);
        }
    }

    public void disconnect() throws MqttException {
        if (this.mqttClient.isConnected()) {
            this.mqttClient.disconnect();
            logger.info("MQTT Client disconnected.");
        }
    }

    public <T> void publish(String topic, T payload) {
        try {
            if (this.mqttClient.isConnected()) {
                Optional<byte[]> serializedPayload = serializePayload(payload);
                if (serializedPayload.isPresent()) {
                    this.mqttClient.publish(topic, serializedPayload.get(), 1, false);
                    logger.debug("Published to topic '{}' with payload: {}", topic, payload);
                }
            } else {
                logger.warn("MQTT client not connected. Cannot publish message to topic '{}'", topic);
            }
        } catch (MqttException e) {
            logger.error("Error publishing to topic '{}'", topic, e);
        }
    }

    private <T> Optional<byte[]> serializePayload(T payload) {
        try {
            return Optional.of(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing payload", e);
            return Optional.empty();
        }
    }
}
