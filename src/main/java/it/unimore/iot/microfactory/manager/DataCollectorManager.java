package it.unimore.iot.microfactory.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import it.unimore.iot.microfactory.model.QualitySensorData;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataCollectorManager {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectorManager.class);

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "data-collector-manager";
    private static final String TELEMETRY_TOPIC_WILDCARD = "mf/+/+/+/status";

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> digitalTwinState = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DataCollectorManager() throws MqttException {
        this.mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
    }

    public void start() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        this.mqttClient.connect(options);

        logger.info("Data Collector Manager connected to broker: {}", BROKER_URL);

        this.mqttClient.setCallback(new MqttCallback() {
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
                // Not used in this subscriber-only client
            }
        });

        this.mqttClient.subscribe(TELEMETRY_TOPIC_WILDCARD, 1);
        logger.info("Subscribed to topic: {}", TELEMETRY_TOPIC_WILDCARD);

        // Start printing stats periodically
        scheduler.scheduleAtFixedRate(this::printStatistics, 5, 5, TimeUnit.SECONDS);
    }

    private void processMessage(String topic, MqttMessage message) throws java.io.IOException {
        logger.debug("Message arrived from topic '{}': {}", topic, new String(message.getPayload()));

        // mf / <cell> / <type> / <id> / status
        String[] topicParts = topic.split("/");
        if (topicParts.length != 5) {
            logger.warn("Received message on unexpected topic: {}", topic);
            return;
        }

        String cellId = topicParts[1];
        String deviceType = topicParts[2];
        String deviceId = topicParts[3];

        Object data = null;

        switch (deviceType) {
            case "robot":
                data = objectMapper.readValue(message.getPayload(), RobotCellStatus.class);
                if (((RobotCellStatus) data).getStatus() == RobotCellStatusEnum.ALARM) {
                    logger.warn("ALARM DETECTED for Robot {}. Sending RESET command.", deviceId);
                    try {
                        String cmdTopic = String.format("mf/%s/robot/%s/cmd", cellId, deviceId);
                        it.unimore.iot.microfactory.model.Command cmd = new it.unimore.iot.microfactory.model.Command();
                        cmd.setType("RESET");
                        cmd.setTs(System.currentTimeMillis());
                        mqttClient.publish(cmdTopic, objectMapper.writeValueAsBytes(cmd), 1, false);
                    } catch (Exception e) {
                        logger.error("Failed to send RESET command to robot {}", deviceId, e);
                    }
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
            digitalTwinState.put(deviceId, data);
        }
    }

    private void printStatistics() {
        logger.info("-------------------- FACTORY STATUS --------------------");
        if (digitalTwinState.isEmpty()) {
            logger.info("No data received from devices yet.");
            return;
        }

        digitalTwinState.forEach((deviceId, data) ->
                logger.info("Device ID: {} -> Status: {}", deviceId, data.toString())
        );

        digitalTwinState.values().stream()
                .filter(d -> d instanceof QualitySensorData)
                .map(d -> (QualitySensorData) d)
                .findFirst()
                .ifPresent(sensorData -> {
                    int total = sensorData.getTotalProcessed();
                    int bad = sensorData.getBadCount();
                    double defectRate = total > 0 ? (double) bad / total * 100 : 0;
                    logger.info("PRODUCTION STATS --- Total: {}, Bad: {}, Defect Rate: {}%",
                            total, bad, String.format("%.2f", defectRate));
                });

        logger.info("------------------------------------------------------");
    }

    public void stop() throws MqttException {
        scheduler.shutdown();
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("Data Collector Manager stopped.");
    }
}
