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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MqttClientManager {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientManager.class);
    private static final String CLIENT_ID_PREFIX = "iot-device";

    private final String brokerUrl;
    private final String cellId;
    private final String deviceType;
    private final String deviceId;

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;

    public MqttClientManager(String cellId, String deviceType, String deviceId) throws MqttException {
        this.cellId = cellId;
        this.deviceType = deviceType;
        this.deviceId = deviceId;

        // Broker da env con default locale
        this.brokerUrl = Optional.ofNullable(System.getenv("MQTT_BROKER_URL"))
                .orElse("tcp://localhost:1883");

        String clientId = String.format("%s-%s-%s-%s", CLIENT_ID_PREFIX, cellId, deviceType, UUID.randomUUID());
        MqttClientPersistence persistence = new MemoryPersistence();
        this.mqttClient = new MqttClient(brokerUrl, clientId, persistence);
        this.objectMapper = new ObjectMapper();
    }

    public void connect() throws MqttException {
        if (!this.mqttClient.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            // Credenziali da env (se presenti)
            Optional.ofNullable(System.getenv("MQTT_USERNAME")).ifPresent(options::setUserName);
            Optional.ofNullable(System.getenv("MQTT_PASSWORD"))
                    .map(String::toCharArray)
                    .ifPresent(options::setPassword);

            // LWT (Last Will & Testament) retained su /lwt
            String lwtTopic = String.format("mf/%s/%s/%s/lwt", cellId, deviceType, deviceId);
            options.setWill(lwtTopic, "offline".getBytes(), 1, true);

            this.mqttClient.connect(options);
            logger.info("MQTT client connected to {}", brokerUrl);

            // Messaggio info retained (una tantum)
            publishInfoMessage();
        }
    }

    private void publishInfoMessage() {
        Map<String, Object> info = Map.of(
                "cellId", cellId,
                "type", deviceType,
                "deviceId", deviceId,
                "fw", "0.1.0",
                "online", true,
                "ts", System.currentTimeMillis()
        );
        String infoTopic = String.format("mf/%s/%s/%s/info", cellId, deviceType, deviceId);
        publishRetained(infoTopic, info);
        logger.info("Published retained info to {}", infoTopic);
    }

    public void disconnect() throws MqttException {
        if (this.mqttClient.isConnected()) {
            this.mqttClient.disconnect();
            logger.info("MQTT client disconnected.");
        }
    }

    public <T> void publish(String topic, T payload) {
        try {
            if (this.mqttClient.isConnected()) {
                serializePayload(payload).ifPresent(bytes -> {
                    try {
                        this.mqttClient.publish(topic, bytes, 1, false);
                        logger.debug("Published to {}", topic);
                    } catch (MqttException e) {
                        logger.error("Error publishing to {}", topic, e);
                    }
                });
            } else {
                logger.warn("MQTT client not connected. Cannot publish to {}", topic);
            }
        } catch (Exception e) {
            logger.error("Error in publish for {}", topic, e);
        }
    }

    public <T> void publishRetained(String topic, T payload) {
        try {
            if (this.mqttClient.isConnected()) {
                serializePayload(payload).ifPresent(bytes -> {
                    try {
                        this.mqttClient.publish(topic, bytes, 1, true);
                        logger.debug("Published retained to {}", topic);
                    } catch (MqttException e) {
                        logger.error("Error publishing retained to {}", topic, e);
                    }
                });
            } else {
                logger.warn("MQTT client not connected. Cannot publish retained to {}", topic);
            }
        } catch (Exception e) {
            logger.error("Error in publishRetained for {}", topic, e);
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

    public IMqttClient getClient() {
        return this.mqttClient;
    }
}
