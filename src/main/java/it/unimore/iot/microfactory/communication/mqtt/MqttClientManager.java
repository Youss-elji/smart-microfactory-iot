package it.unimore.iot.microfactory.communication.mqtt;

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

// Gestisce la connessione MQTT lato dispositivo simulato fornendo publish e messaggi informativi
public class MqttClientManager {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientManager.class);
    private static final String CLIENT_ID_PREFIX = "iot-device";

    private final String brokerUrl;
    private final String cellId;
    private final String deviceType;
    private final String deviceId;

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;

    // Costruttore che prepara il client MQTT e definisce identificativi per topic e clientId
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

    // Apre la connessione al broker configurando credenziali, LWT e messaggio informativo retained
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

            // Configura il Last Will & Testament (LWT) retained sul topic /lwt
            String lwtTopic = String.format("mf/%s/%s/%s/lwt", cellId, deviceType, deviceId);
            options.setWill(lwtTopic, "offline".getBytes(), 1, true);

            this.mqttClient.connect(options);
            logger.info("MQTT client connected to {}", brokerUrl);

            // Messaggio info retained (una tantum)
            publishInfoMessage();
        }
    }

    // Pubblica un messaggio retained che descrive lo stato e le caratteristiche del dispositivo
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

    // Chiude la connessione MQTT quando il dispositivo si arresta
    public void disconnect() throws MqttException {
        if (this.mqttClient.isConnected()) {
            this.mqttClient.disconnect();
            logger.info("MQTT client disconnected.");
        }
    }

    // Pubblica un messaggio QoS1 non retained sul topic specificato serializzando l'oggetto in JSON
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

    // Pubblica un messaggio QoS1 retained per mantenere l'ultimo valore disponibile ai subscriber
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

    // Serializza un payload generico in JSON, restituendo i byte da inviare su MQTT
    private <T> Optional<byte[]> serializePayload(T payload) {
        try {
            return Optional.of(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing payload", e);
            return Optional.empty();
        }
    }

    // Espone il client MQTT sottostante per eventuali operazioni avanzate
    public IMqttClient getClient() {
        return this.mqttClient;
    }
}
