package it.unimore.iot.microfactory.device.simulator;

import it.unimore.iot.microfactory.model.QualitySensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

// Sensore di qualità simulato che analizza pezzi e pubblica statistiche di conformità
public class QualitySensor extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(QualitySensor.class);

    private static final int SCAN_INTERVAL_MS = 1500;
    private static final double GOOD_QUALITY_PROBABILITY = 0.95;

    private final Random random = new Random();
    private int totalProcessed = 0;
    private int goodCount = 0;
    private int badCount = 0;

    private final String statusTopic;

    // Costruttore che definisce il topic di telemetria del sensore di qualità
    public QualitySensor(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.statusTopic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
    }

    // Loop principale che scandisce nuovi pezzi a intervalli casualizzati e pubblica i risultati
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

    // Simula il controllo qualità di un singolo pezzo aggiornando i conteggi aggregati
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

    // Pubblica lo stato cumulativo del sensore sul topic dedicato
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
