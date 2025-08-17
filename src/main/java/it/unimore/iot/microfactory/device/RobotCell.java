package it.unimore.iot.microfactory.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class RobotCell extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(RobotCell.class);
    private static final String TELEMETRY_TOPIC_TPL = "mf/%s/%s/%s/status";
    private static final String COMMAND_TOPIC_TPL = "mf/%s/%s/%s/cmd";
    private static final String ACK_TOPIC_TPL = "mf/%s/%s/%s/ack";

    private static final int IDLE_DURATION_MS = 2000;
    private static final int MAX_PROCESSING_DURATION_MS = 5000;
    private static final double ALARM_PROBABILITY = 0.1;

    private final Random random = new Random();
    private final ObjectMapper objectMapper;
    private RobotCellStatusEnum currentState = RobotCellStatusEnum.IDLE;
    private boolean active = true; // Robot is active by default

    public RobotCell(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run() {
        try {
            mqttClientManager.connect();
            subscribeToCommands();
            start();
        } catch (Exception e) {
            logger.error("Error during device execution for {}", deviceId, e);
        } finally {
            try {
                mqttClientManager.disconnect();
            } catch (Exception e) {
                logger.error("Error disconnecting MQTT client for device {}", deviceId, e);
            }
        }
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("RobotCell {} started.", deviceId);

        while (running && !Thread.currentThread().isInterrupted()) {
            if (active) {
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
            } else {
                // If not active, just wait
                Thread.sleep(IDLE_DURATION_MS);
            }
        }
    }

    private void handleIdleState() throws InterruptedException {
        publishStatus(0);
        Thread.sleep(IDLE_DURATION_MS);
        if (active) {
            this.currentState = RobotCellStatusEnum.PROCESSING;
            logger.info("Robot {} state changed to PROCESSING", deviceId);
        }
    }

    private void handleProcessingState() throws InterruptedException {
        double processingTime = random.nextInt(MAX_PROCESSING_DURATION_MS);
        publishStatus(processingTime / 1000.0);
        Thread.sleep((long) processingTime);

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
        logger.error("Robot {} is in ALARM state. Waiting for reset command...", deviceId);
        // Stay in alarm until a RESET command is received
        while (currentState == RobotCellStatusEnum.ALARM && running) {
            Thread.sleep(1000);
        }
    }

    private void subscribeToCommands() {
        try {
            String cmdTopic = String.format(COMMAND_TOPIC_TPL, this.cellId, this.deviceType, this.deviceId);
            int qos = 1;
            this.mqttClientManager.getClient().subscribe(cmdTopic, qos, (topic, msg) -> {
                try {
                    Command cmd = objectMapper.readValue(msg.getPayload(), Command.class);
                    handleCommand(cmd);
                } catch (Exception e) {
                    logger.error("Error processing command", e);
                }
            });
        } catch (Exception e) {
            logger.error("Error subscribing to command topic", e);
        }
    }

    private void handleCommand(Command cmd) {
        String status = "OK";
        String message = "Command executed successfully";
        logger.info("Command received: {}", cmd.getType());

        switch (cmd.getType().toUpperCase()) {
            case "START":
                this.active = true;
                break;
            case "STOP":
                this.active = false;
                this.currentState = RobotCellStatusEnum.IDLE;
                break;
            case "RESET":
                if (this.currentState == RobotCellStatusEnum.ALARM) {
                    this.currentState = RobotCellStatusEnum.IDLE;
                } else {
                    status = "ERROR";
                    message = "Reset command only valid in ALARM state";
                }
                break;
            default:
                status = "ERROR";
                message = "Unknown command type";
                logger.warn("Unknown command received: {}", cmd.getType());
                break;
        }
        publishAck(cmd.getType(), status, message);
    }

    private void publishAck(String cmdType, String status, String message) {
        try {
            Ack ack = new Ack(cmdType, status, message, System.currentTimeMillis());
            String ackTopic = String.format(ACK_TOPIC_TPL, this.cellId, this.deviceType, this.deviceId);
            this.mqttClientManager.publish(ackTopic, ack);
        } catch (Exception e) {
            logger.error("Error publishing ACK", e);
        }
    }

    private void publishStatus(double processingTime) {
        RobotCellStatus status = new RobotCellStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.currentState,
                processingTime
        );
        String topic = String.format(TELEMETRY_TOPIC_TPL, this.cellId, this.deviceType, this.deviceId);
        mqttClientManager.publish(topic, status);
    }
}
