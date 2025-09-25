package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.QualitySensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class QualitySensor extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(QualitySensor.class);

    private static final int SCAN_INTERVAL_MS = 1500;
    private static final double GOOD_QUALITY_PROBABILITY = 0.95;

    private final Random random = new Random();
    private int totalProcessed = 0;
    private int goodCount = 0;
    private int badCount = 0;

    private final String statusTopic;

    public QualitySensor(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.statusTopic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("QualitySensor {} started.", deviceId);

        while (running) {
            Thread.sleep(SCAN_INTERVAL_MS + random.nextInt(500));
            if (running) {
                scanNewItem();
                publishStatus();
            }
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
        mqttClientManager.publish(statusTopic, data);
    }
}
