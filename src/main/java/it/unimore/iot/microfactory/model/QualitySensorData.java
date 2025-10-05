package it.unimore.iot.microfactory.model;

/**
 * Rappresenta i dati di telemetria inviati da un sensore di controllo qualità.
 * Questo POJO aggrega le metriche sul conteggio dei pezzi processati.
 */
public class QualitySensorData {

    /**
     * Identificativo univoco del dispositivo (es. "quality-sensor-001").
     */
    private String deviceId;

    /**
     * Timestamp UNIX (in millisecondi) che indica quando i dati sono stati registrati.
     */
    private long timestamp;

    /**
     * Il numero totale di oggetti processati dal sensore fino a questo momento.
     */
    private int totalProcessed;

    /**
     * Il numero di oggetti risultati conformi (di buona qualità).
     */
    private int goodCount;

    /**
     * Il numero di oggetti risultati difettosi (di cattiva qualità).
     */
    private int badCount;

    public QualitySensorData() {
    }

    public QualitySensorData(String deviceId, long timestamp, int totalProcessed, int goodCount, int badCount) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.totalProcessed = totalProcessed;
        this.goodCount = goodCount;
        this.badCount = badCount;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public int getGoodCount() {
        return goodCount;
    }

    public void setGoodCount(int goodCount) {
        this.goodCount = goodCount;
    }

    public int getBadCount() {
        return badCount;
    }

    public void setBadCount(int badCount) {
        this.badCount = badCount;
    }

    @Override
    public String toString() {
        return "QualitySensorData{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp +
                ", totalProcessed=" + totalProcessed +
                ", goodCount=" + goodCount +
                ", badCount=" + badCount +
                '}';
    }
}