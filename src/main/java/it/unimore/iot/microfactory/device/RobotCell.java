package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class RobotCell extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(RobotCell.class);
    private static final String TELEMETRY_TOPIC = "microfactory/robot/%s/status";

    private static final int IDLE_DURATION_MS = 2000;
    private static final int MAX_PROCESSING_DURATION_MS = 5000;
    private static final double ALARM_PROBABILITY = 0.1;

    private final Random random = new Random();
    private RobotCellStatusEnum currentState = RobotCellStatusEnum.IDLE;

    public RobotCell(String deviceId) {
        super(deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("RobotCell {} started.", deviceId);

        while (!Thread.currentThread().isInterrupted()) {
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
            // Small delay to prevent tight loop in case of unhandled state
            Thread.sleep(100);
        }
    }

    private void handleIdleState() throws InterruptedException {
        publishStatus(0);
        Thread.sleep(IDLE_DURATION_MS);
        this.currentState = RobotCellStatusEnum.PROCESSING;
        logger.info("Robot {} state changed to PROCESSING", deviceId);
    }

    private void handleProcessingState() throws InterruptedException {
        double processingTime = random.nextInt(MAX_PROCESSING_DURATION_MS);
        publishStatus(processingTime / 1000.0); // Convert to seconds
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
        // In a real scenario, the robot would wait for a command to reset.
        // Here we simulate a manual reset after a delay.
        logger.error("Robot {} is in ALARM state. Waiting for reset...", deviceId);
        Thread.sleep(10000); // Stay in alarm for 10 seconds
        this.currentState = RobotCellStatusEnum.IDLE;
        logger.info("Robot {} reset, state changed to IDLE", deviceId);
    }

    private void publishStatus(double processingTime) {
        RobotCellStatus status = new RobotCellStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.currentState,
                processingTime
        );
        String topic = String.format(TELEMETRY_TOPIC, this.deviceId);
        mqttClientManager.publish(topic, status);
    }
}
