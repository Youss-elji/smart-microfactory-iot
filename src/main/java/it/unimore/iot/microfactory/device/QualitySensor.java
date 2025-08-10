package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.QualitySensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class QualitySensor extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(QualitySensor.class);
    private static final String TELEMETRY_TOPIC = "microfactory/sensor/%s/status";

    private static final int SCAN_INTERVAL_MS = 1500;
    private static final double GOOD_QUALITY_PROBABILITY = 0.95;

    private final Random random = new Random();
    private int totalProcessed = 0;
    private int goodCount = 0;
    private int badCount = 0;

    public QualitySensor(String deviceId) {
        super(deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("QualitySensor {} started.", deviceId);

        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(SCAN_INTERVAL_MS + random.nextInt(500));
            scanNewItem();
            publishStatus();
        }
    }

    private void scanNewItem() {
        this.totalProcessed++;
        if (random.nextDouble() < GOOD_QUALITY_PROBABILITY) {
            this.goodCount++;
            logger.debug("Device {}: Item #{} is GOOD", deviceId, totalProcessed);
        } else {
            this.badCount++;
            logger.warn("Device {}: Item #{} is BAD", deviceId, totalProcessed);
        }
    }

    private void publishStatus() {
        QualitySensorData data = new QualitySensorData(
                this.deviceId,
                System.currentTimeMillis(),
                this.totalProcessed,
                this.goodCount,
                this.badCount
        );
        String topic = String.format(TELEMETRY_TOPIC, this.deviceId);
        mqttClientManager.publish(topic, data);
    }
}
