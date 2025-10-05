package it.unimore.iot.microfactory.model;

/**
 * Rappresenta lo stato di un nastro trasportatore in un dato istante.
 * Questo POJO è utilizzato per la telemetria del dispositivo.
 */
public class ConveyorBeltStatus {

    /**
     * Identificativo univoco del dispositivo (es. "conveyor-001").
     */
    private String deviceId;

    /**
     * Timestamp UNIX (in millisecondi) che indica quando lo stato è stato registrato.
     */
    private long timestamp;

    /**
     * Indica se il nastro trasportatore è attualmente in funzione.
     * {@code true} se attivo, {@code false} altrimenti.
     */
    private boolean active;

    /**
     * La velocità del nastro, espressa ad esempio in oggetti al minuto.
     */
    private double speed;

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