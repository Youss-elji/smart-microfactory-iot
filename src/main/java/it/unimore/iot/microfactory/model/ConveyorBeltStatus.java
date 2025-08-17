package it.unimore.iot.microfactory.model;

public class ConveyorBeltStatus {

    private String deviceId;
    private long timestamp;
    private boolean active;
    private double speed; // e.g., items per minute

    public ConveyorBeltStatus() {
    }

    public ConveyorBeltStatus(String deviceId, long timestamp, boolean active, double speed) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.active = active;
        this.speed = speed;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public String toString() {
        return "ConveyorBeltStatus{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp +
                ", active=" + active +
                ", speed=" + speed +
                '}';
    }
}
