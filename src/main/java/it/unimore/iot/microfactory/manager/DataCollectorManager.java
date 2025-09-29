package it.unimore.iot.microfactory.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.domain.StateRepository;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import it.unimore.iot.microfactory.model.QualitySensorData;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
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
    private final StateRepository stateRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DataCollectorManager() throws MqttException {
        this.mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
        this.stateRepository = StateRepository.getInstance();
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
                // Not used
            }
        });

        this.mqttClient.subscribe(TELEMETRY_TOPIC_WILDCARD, 1);
        logger.info("Subscribed to topic: {}", TELEMETRY_TOPIC_WILDCARD);

        scheduler.scheduleAtFixedRate(this::printStatistics, 10, 10, TimeUnit.SECONDS);
    }

    private record TopicParts(String cell, String type, String id) {}

    private Optional<TopicParts> parseTopic(String topic) {
        String[] topicParts = topic.split("/");
        if (topicParts.length == 5 && topicParts[0].equals("mf") && topicParts[4].equals("status")) {
            return Optional.of(new TopicParts(topicParts[1], topicParts[2], topicParts[3]));
        }
        logger.warn("Received message on unexpected topic format: {}", topic);
        return Optional.empty();
    }

    private void processMessage(String topic, MqttMessage message) throws IOException {
        logger.debug("Message arrived from topic '{}'", topic);

        parseTopic(topic).ifPresent(topicParts -> {
            try {
                Object data = null;
                switch (topicParts.type()) {
                    case "robot":
                        data = objectMapper.readValue(message.getPayload(), RobotCellStatus.class);
                        if (((RobotCellStatus) data).getStatus() == RobotCellStatusEnum.ALARM) {
                            logger.warn("ALARM DETECTED for Robot {} in cell {}. No action taken.", topicParts.id(), topicParts.cell());
                        }
                        break;
                    case "conveyor":
                        data = objectMapper.readValue(message.getPayload(), ConveyorBeltStatus.class);
                        break;
                    case "quality":
                        data = objectMapper.readValue(message.getPayload(), QualitySensorData.class);
                        break;
                    default:
                        logger.warn("Unknown device type in topic: {}", topicParts.type());
                }

                if (data != null) {
                    stateRepository.upsert(topicParts.cell(), topicParts.type(), topicParts.id(), data);
                }
            } catch (IOException e) {
                logger.error("Error deserializing message payload for topic {}", topic, e);
            }
        });
    }

    private void printStatistics() {
        logger.info("Periodic check... (statistics could be generated from StateRepository)");
    }

    public void stop() throws MqttException {
        scheduler.shutdown();
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("Data Collector Manager stopped.");
    }
}
