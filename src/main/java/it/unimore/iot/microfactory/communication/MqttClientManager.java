package it.unimore.iot.microfactory.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class MqttClientManager {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientManager.class);

    private static final String DEFAULT_BROKER = "tcp://localhost:1883";
    private static final String BROKER_URL = System.getenv().getOrDefault("MQTT_BROKER_URL", DEFAULT_BROKER);
    private static final String BROKER_USER = System.getenv().getOrDefault("MQTT_USERNAME", "");
    private static final String BROKER_PASS = System.getenv().getOrDefault("MQTT_PASSWORD", "");

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final String cellId;
    private final String deviceType;
    private final String deviceId;

    public MqttClientManager(String cellId, String deviceType, String deviceId) throws MqttException {
        this.cellId = cellId;
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        String clientId = "iot-device-" + deviceType + "-" + deviceId;

        this.mqttClient = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());
        this.objectMapper = new ObjectMapper();
    }

    public void connect() throws MqttException {
        if (!mqttClient.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            if (!BROKER_USER.isEmpty()) {
                options.setUserName(BROKER_USER);
                options.setPassword(BROKER_PASS.toCharArray());
            }

            // LWT: offline
            String lwtTopic = String.format("mf/%s/%s/%s/lwt", cellId, deviceType, deviceId);
            options.setWill(lwtTopic, "offline".getBytes(), 1, true);

            mqttClient.connect(options);
            logger.info("MQTT connected: {}", BROKER_URL);

            // INFO retained (online + metadati)
            publishInfoRetained();
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

    public void publishInfoRetained() {
        try {
            var info = Map.of(
                    "deviceId", deviceId,
                    "type", deviceType,
                    "cellId", cellId,
                    "fw", "0.1.0",
                    "online", true,
                    "ts", System.currentTimeMillis()
            );
            var payload = objectMapper.writeValueAsBytes(info);
            String infoTopic = String.format("mf/%s/%s/%s/info", cellId, deviceType, deviceId);
            mqttClient.publish(infoTopic, payload, 1, true);
        } catch (Exception e) {
            logger.warn("Cannot publish retained info for {}", deviceId, e);
        }
    }

    public IMqttClient getClient() {
        return this.mqttClient;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
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
