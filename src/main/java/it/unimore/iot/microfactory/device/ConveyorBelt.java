package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ConveyorBelt extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(ConveyorBelt.class);
    private static final String TELEMETRY_TOPIC = "microfactory/conveyor/%s/status";

    private static final int ACTIVE_DURATION_MS = 15000;
    private static final int INACTIVE_DURATION_MS = 5000;
    private static final double BASE_SPEED = 10.0; // items per minute
    private static final double SPEED_VARIATION = 2.0;

    private final Random random = new Random();
    private boolean active = false;

    public ConveyorBelt(String deviceId) {
        super(deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("ConveyorBelt {} started.", deviceId);

        while (!Thread.currentThread().isInterrupted()) {
            if (active) {
                // Simulate being active
                logger.info("Conveyor belt {} is ON", deviceId);
                publishStatus();
                Thread.sleep(ACTIVE_DURATION_MS + random.nextInt(3000));
                this.active = false;
            } else {
                // Simulate being inactive
                logger.info("Conveyor belt {} is OFF", deviceId);
                publishStatus();
                Thread.sleep(INACTIVE_DURATION_MS + random.nextInt(1000));
                this.active = true;
            }
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
        String topic = String.format(TELEMETRY_TOPIC, this.deviceId);
        mqttClientManager.publish(topic, status);
    }
}
