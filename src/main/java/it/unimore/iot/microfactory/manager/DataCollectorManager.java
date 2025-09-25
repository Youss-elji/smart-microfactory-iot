package it.unimore.iot.microfactory.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataCollectorManager {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectorManager.class);

    private static final String CLIENT_ID = "data-collector-manager-" + UUID.randomUUID();
    private static final String TELEMETRY_TOPIC_WILDCARD = "mf/+/+/+/status";

    private final String brokerUrl;
    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> digitalTwinState = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DataCollectorManager() throws MqttException {
        this.brokerUrl = Optional.ofNullable(System.getenv("MQTT_BROKER_URL")).orElse("tcp://localhost:1883");
        this.mqttClient = new MqttClient(brokerUrl, CLIENT_ID, new MemoryPersistence());
    }

    public void start() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        Optional.ofNullable(System.getenv("MQTT_USERNAME")).ifPresent(options::setUserName);
        Optional.ofNullable(System.getenv("MQTT_PASSWORD"))
                .map(String::toCharArray)
                .ifPresent(options::setPassword);

        this.mqttClient.connect(options);
        logger.info("Data Collector Manager connected to broker: {}", brokerUrl);

        this.mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                logger.info("Connection complete to {}. Reconnect: {}", serverURI, reconnect);
                try {
                    subscribeToTopics();
                } catch (MqttException e) {
                    logger.error("Error subscribing to topics after (re)connection", e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                logger.error("Connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                try {
                    processMessage(topic, message);
                } catch (Exception e) {
                    logger.error("Error processing message from topic {}", topic, e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for publishing from this callback
            }
        });

        subscribeToTopics();
        scheduler.scheduleAtFixedRate(this::printStatistics, 10, 10, TimeUnit.SECONDS);
    }

    private void subscribeToTopics() throws MqttException {
        this.mqttClient.subscribe(TELEMETRY_TOPIC_WILDCARD, 1);
        logger.info("Subscribed to topic: {}", TELEMETRY_TOPIC_WILDCARD);
    }

    private void processMessage(String topic, MqttMessage message) throws java.io.IOException {
        logger.debug("Message arrived from topic '{}': {}", topic, new String(message.getPayload()));
        String[] topicParts = topic.split("/");
        if (topicParts.length != 5 || !topicParts[0].equals("mf") || !topicParts[4].equals("status")) {
            logger.warn("Received message on unexpected topic format: {}", topic);
            return;
        }

        String cellId = topicParts[1];
        String deviceType = topicParts[2];
        String deviceId = topicParts[3];
        String digitalTwinKey = String.format("%s-%s", deviceType, deviceId);

        Object data = null;

        switch (deviceType) {
            case "robot":
                RobotCellStatus status = objectMapper.readValue(message.getPayload(), RobotCellStatus.class);
                data = status;
                if (status.getStatus() == RobotCellStatusEnum.ALARM) {
                    logger.warn("ALARM DETECTED for Robot {} in cell {}. Sending RESET command.", deviceId, cellId);
                    sendResetCommand(cellId, deviceId);
                }
                break;
            case "conveyor":
                data = objectMapper.readValue(message.getPayload(), ConveyorBeltStatus.class);
                break;
            case "quality":
                data = objectMapper.readValue(message.getPayload(), QualitySensorData.class);
                break;
            default:
                logger.warn("Unknown device type in topic: {}", deviceType);
                return;
        }

        if (data != null) {
            digitalTwinState.put(digitalTwinKey, data);
        }
    }

    private void sendResetCommand(String cellId, String deviceId) {
        try {
            String cmdTopic = String.format("mf/%s/robot/%s/cmd", cellId, deviceId);
            Command cmd = new Command("RESET", System.currentTimeMillis());
            byte[] payload = objectMapper.writeValueAsBytes(cmd);
            mqttClient.publish(cmdTopic, payload, 1, false);
            logger.info("Published RESET command to {}", cmdTopic);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing RESET command", e);
        } catch (MqttException e) {
            logger.error("Error publishing RESET command", e);
        }
    }

    private void printStatistics() {
        logger.info("-------------------- FACTORY STATUS --------------------");
        if (digitalTwinState.isEmpty()) {
            logger.info("No data received from devices yet.");
            return;
        }

        digitalTwinState.forEach((key, data) -> logger.info("Device [{}]: {}", key, data.toString()));

        digitalTwinState.values().stream()
                .filter(d -> d instanceof QualitySensorData)
                .map(d -> (QualitySensorData) d)
                .findFirst()
                .ifPresent(sensorData -> {
                    int total = sensorData.getTotalProcessed();
                    int bad = sensorData.getBadCount();
                    double defectRate = total > 0 ? (double) bad / total * 100 : 0;
                    logger.info("--- PRODUCTION STATS --- Total: {}, Bad: {}, Defect Rate: {:.2f}%", total, bad, defectRate);
                });
        logger.info("------------------------------------------------------");
    }

    public void stop() throws MqttException {
        scheduler.shutdownNow();
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("Data Collector Manager stopped.");
    }
}
