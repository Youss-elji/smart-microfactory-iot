package it.unimore.iot.microfactory.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.domain.StateRepository;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import it.unimore.iot.microfactory.model.QualitySensorData;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
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
    private final StateRepository stateRepository = StateRepository.getInstance();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // abilita/disabilita l'auto-reset del robot in ALARM (default: true)
    private final boolean autoResetOnAlarm =
            Boolean.parseBoolean(Optional.ofNullable(System.getenv("AUTO_RESET_ON_ALARM")).orElse("true"));

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

        mqttClient.setCallback(new MqttCallbackExtended() {
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
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                // not used
            }
        });

        mqttClient.connect(options);
        logger.info("Data Collector Manager connected to broker: {}", brokerUrl);

        subscribeToTopics();
        scheduler.scheduleAtFixedRate(this::printStatistics, 10, 10, TimeUnit.SECONDS);
    }

    private void subscribeToTopics() throws MqttException {
        mqttClient.subscribe(TELEMETRY_TOPIC_WILDCARD, 1);
        logger.info("Subscribed to topic: {}", TELEMETRY_TOPIC_WILDCARD);
    }

    private record TopicParts(String cell, String type, String id) {}

    private Optional<TopicParts> parseTopic(String topic) {
        String[] p = topic.split("/");
        if (p.length == 5 && "mf".equals(p[0]) && "status".equals(p[4])) {
            return Optional.of(new TopicParts(p[1], p[2], p[3]));
        }
        logger.warn("Received message on unexpected topic format: {}", topic);
        return Optional.empty();
    }

    private void processMessage(String topic, MqttMessage message) throws IOException {
        logger.debug("Message arrived from topic '{}'", topic);

        parseTopic(topic).ifPresent(parts -> {
            try {
                Object data = null;
                switch (parts.type()) {
                    case "robot" -> {
                        RobotCellStatus status = objectMapper.readValue(message.getPayload(), RobotCellStatus.class);
                        data = status;
                        if (autoResetOnAlarm && status.getStatus() == RobotCellStatusEnum.ALARM) {
                            logger.warn("ALARM for Robot {} in cell {}. Sending RESET.", parts.id(), parts.cell());
                            sendResetCommand(parts.cell(), parts.id());
                        }
                    }
                    case "conveyor" -> data = objectMapper.readValue(message.getPayload(), ConveyorBeltStatus.class);
                    case "quality"  -> data = objectMapper.readValue(message.getPayload(), QualitySensorData.class);
                    default         -> logger.warn("Unknown device type in topic: {}", parts.type());
                }

                if (data != null) {
                    stateRepository.upsert(parts.cell(), parts.type(), parts.id(), data);
                }
            } catch (IOException e) {
                logger.error("Error deserializing message payload for topic {}", topic, e);
            }
        });
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
        // placeholder: qui puoi calcolare KPI a partire dallo StateRepository
        logger.info("Periodic check... (you can compute KPIs from StateRepository here)");
    }

    public void stop() throws MqttException {
        scheduler.shutdownNow();
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("Data Collector Manager stopped.");
    }
}
