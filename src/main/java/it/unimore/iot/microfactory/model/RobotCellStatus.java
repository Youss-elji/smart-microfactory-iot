package it.unimore.iot.microfactory.model;

public class RobotCellStatus {

    private String deviceId;
    private long timestamp;
    private RobotCellStatusEnum status;
    private double processingTime; // e.g., in seconds

    public RobotCellStatus() {
    }

    public RobotCellStatus(String deviceId, long timestamp, RobotCellStatusEnum status, double processingTime) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.status = status;
        this.processingTime = processingTime;
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

    public RobotCellStatusEnum getStatus() {
        return status;
    }

    public void setStatus(RobotCellStatusEnum status) {
        this.status = status;
    }

    public double getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(double processingTime) {
        this.processingTime = processingTime;
    }

    @Override
    public String toString() {
        return "RobotCellStatus{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp +
                ", status=" + status +
                ", processingTime=" + processingTime +
                '}';
    }
}
