package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ConveyorBelt extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(ConveyorBelt.class);

    private static final double BASE_SPEED = 10.0; // items per minute
    private static final double SPEED_VARIATION = 2.0;
    private static final int TELEMETRY_PUBLISH_INTERVAL_MS = 5000;

    private final Random random = new Random();
    private boolean active = false;

    private final String statusTopic;
    private final String cmdTopic;
    private final String ackTopic;

    public ConveyorBelt(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.statusTopic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
        this.cmdTopic = String.format("mf/%s/%s/%s/cmd", cellId, deviceType, deviceId);
        this.ackTopic = String.format("mf/%s/%s/%s/ack", cellId, deviceType, deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("ConveyorBelt {} started.", deviceId);
        subscribeToCommands();

        while (running) {
            publishStatus();
            Thread.sleep(TELEMETRY_PUBLISH_INTERVAL_MS);
        }
    }

    private void subscribeToCommands() {
        try {
            mqttClientManager.getClient().subscribe(cmdTopic, 1, this::handleCommandMessage);
            logger.info("Subscribed to command topic: {}", cmdTopic);
        } catch (MqttException e) {
            logger.error("Failed to subscribe to command topic {}", cmdTopic, e);
        }
    }

    private void handleCommandMessage(String topic, MqttMessage message) {
        try {
            Command cmd = objectMapper.readValue(message.getPayload(), Command.class);
            logger.info("Received command: {} on topic {}", cmd.getType(), topic);
            handleCommand(cmd);
        } catch (Exception e) {
            logger.error("Error processing command message", e);
        }
    }

    private void handleCommand(Command cmd) {
        String status = "OK";
        String responseMessage = "Command executed successfully";

        switch (cmd.getType()) {
            case "START":
                if (!this.active) {
                    this.active = true;
                    logger.info("Conveyor belt {} is now ON", deviceId);
                }
                break;
            case "STOP":
                if (this.active) {
                    this.active = false;
                    logger.info("Conveyor belt {} is now OFF", deviceId);
                }
                break;
            case "RESET":
                // No special logic for reset, but we acknowledge it
                break;
            default:
                status = "ERROR";
                responseMessage = "Unknown command type: " + cmd.getType();
                logger.warn(responseMessage);
        }
        publishAck(cmd.getType(), status, responseMessage);
    }

    private void publishStatus() {
        double currentSpeed = 0;
        if (this.active) {
            currentSpeed = BASE_SPEED + (random.nextDouble() * SPEED_VARIATION * 2) - SPEED_VARIATION;
            currentSpeed = Math.max(0, currentSpeed); // Ensure speed is not negative
        }

        ConveyorBeltStatus status = new ConveyorBeltStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.active,
                currentSpeed
        );
        mqttClientManager.publish(statusTopic, status);
    }

    private void publishAck(String cmdType, String status, String message) {
        Ack ack = new Ack(cmdType, status, message, System.currentTimeMillis());
        mqttClientManager.publish(ackTopic, ack);
    }
}
