package it.unimore.iot.microfactory.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ConveyorBelt extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(ConveyorBelt.class);
    private static final String TELEMETRY_TOPIC_TPL = "mf/%s/%s/%s/status";
    private static final String COMMAND_TOPIC_TPL = "mf/%s/%s/%s/cmd";
    private static final String ACK_TOPIC_TPL = "mf/%s/%s/%s/ack";

    private static final int SIMULATION_STEP_MS = 2000;
    private static final double BASE_SPEED = 10.0; // items per minute
    private static final double SPEED_VARIATION = 2.0;

    private final Random random = new Random();
    private final ObjectMapper objectMapper;
    private boolean active = true; // Conveyor is active by default

    public ConveyorBelt(String cellId, String deviceType, String deviceId) {
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
        logger.info("ConveyorBelt {} started.", deviceId);

        while (running && !Thread.currentThread().isInterrupted()) {
            publishStatus();
            Thread.sleep(SIMULATION_STEP_MS + random.nextInt(500));
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
        String topic = String.format(TELEMETRY_TOPIC_TPL, this.cellId, this.deviceType, this.deviceId);
        mqttClientManager.publish(topic, status);
    }
}
