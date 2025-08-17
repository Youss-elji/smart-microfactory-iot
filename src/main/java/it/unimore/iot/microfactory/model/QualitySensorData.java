package it.unimore.iot.microfactory.model;

public class QualitySensorData {

    private String deviceId;
    private long timestamp;
    private int totalProcessed;
    private int goodCount;
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
