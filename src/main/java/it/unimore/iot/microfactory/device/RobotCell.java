package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class RobotCell extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(RobotCell.class);

    private static final int IDLE_DURATION_MS = 2000;
    private static final int MAX_PROCESSING_DURATION_MS = 5000;
    private static final double ALARM_PROBABILITY = 0.1;

    private final Random random = new Random();
    private RobotCellStatusEnum currentState = RobotCellStatusEnum.IDLE;

    private final String statusTopic;
    private final String cmdTopic;
    private final String ackTopic;

    public RobotCell(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.statusTopic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
        this.cmdTopic = String.format("mf/%s/%s/%s/cmd", cellId, deviceType, deviceId);
        this.ackTopic = String.format("mf/%s/%s/%s/ack", cellId, deviceType, deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("RobotCell {} started.", deviceId);
        subscribeToCommands();

        while (running) {
            switch (currentState) {
                case IDLE:
                    handleIdleState();
                    break;
                case PROCESSING:
                    handleProcessingState();
                    break;
                case ALARM:
                    handleAlarmState();
                    break;
            }
            Thread.sleep(100); // Main loop delay
        }
    }

    private void handleIdleState() throws InterruptedException {
        publishStatus(0);
        Thread.sleep(IDLE_DURATION_MS);
        if (running) {
            this.currentState = RobotCellStatusEnum.PROCESSING;
            logger.info("Robot {} state changed to PROCESSING", deviceId);
        }
    }

    private void handleProcessingState() throws InterruptedException {
        double processingTime = random.nextInt(MAX_PROCESSING_DURATION_MS);
        publishStatus(processingTime / 1000.0);
        Thread.sleep((long) processingTime);

        if (!running) return; // Exit if shutdown was called during sleep

        if (random.nextDouble() < ALARM_PROBABILITY) {
            this.currentState = RobotCellStatusEnum.ALARM;
            logger.warn("Robot {} state changed to ALARM", deviceId);
        } else {
            this.currentState = RobotCellStatusEnum.IDLE;
            logger.info("Robot {} finished processing, state changed to IDLE", deviceId);
        }
    }

    private void handleAlarmState() throws InterruptedException {
        publishStatus(0);
        logger.error("Robot {} is in ALARM state. Waiting for external RESET command...", deviceId);
        while (running && this.currentState == RobotCellStatusEnum.ALARM) {
            Thread.sleep(1000); // Wait until state is changed by a command
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
        String message = "Command executed successfully";

        switch (cmd.getType()) {
            case "START":
                if (currentState == RobotCellStatusEnum.IDLE) {
                    this.currentState = RobotCellStatusEnum.PROCESSING;
                } else {
                    status = "ERROR";
                    message = "Cannot start, not in IDLE state.";
                }
                break;
            case "STOP":
                this.currentState = RobotCellStatusEnum.IDLE;
                break;
            case "RESET":
                if (currentState == RobotCellStatusEnum.ALARM) {
                    this.currentState = RobotCellStatusEnum.IDLE;
                    logger.info("Robot {} reset from ALARM state.", deviceId);
                } else {
                    status = "ERROR";
                    message = "Cannot reset, not in ALARM state.";
                }
                break;
            default:
                status = "ERROR";
                message = "Unknown command type: " + cmd.getType();
                logger.warn(message);
        }
        publishAck(cmd.getType(), status, message);
    }

    private void publishStatus(double processingTime) {
        RobotCellStatus status = new RobotCellStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.currentState,
                processingTime
        );
        mqttClientManager.publish(statusTopic, status);
    }

    private void publishAck(String cmdType, String status, String message) {
        Ack ack = new Ack(cmdType, status, message, System.currentTimeMillis());
        mqttClientManager.publish(ackTopic, ack);
    }
}
