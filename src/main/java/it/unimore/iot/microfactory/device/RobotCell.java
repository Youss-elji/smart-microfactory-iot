package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
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

    }

    @Override
    public void start() throws InterruptedException {
        logger.info("RobotCell {} started.", deviceId);

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
        this.currentState = RobotCellStatusEnum.IDLE;
    }

    private void publishStatus(double processingTime) {
        RobotCellStatus status = new RobotCellStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.currentState,
                processingTime
        );
        mqttClientManager.publish(topic, status);
    }
}
