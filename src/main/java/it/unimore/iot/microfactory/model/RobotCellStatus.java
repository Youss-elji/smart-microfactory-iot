package it.unimore.iot.microfactory.model;

/**
 * Rappresenta lo stato di una cella robotica in un dato istante.
 * Questa classe è un Plain Old Java Object (POJO) utilizzato per la serializzazione
 * e deserializzazione dei dati di telemetria del robot.
 */
public class RobotCellStatus {

    /**
     * Identificativo univoco del dispositivo (es. "robot-001").
     */
    private String deviceId;

    /**
     * Timestamp UNIX (in millisecondi) che indica quando lo stato è stato registrato.
     */
    private long timestamp;

    /**
     * Stato operativo attuale del robot (es. IDLE, PROCESSING, ERROR).
     */
    private RobotCellStatusEnum status;

    /**
     * Tempo di processamento dell'ultimo ciclo di lavoro, espresso in secondi.
     */
    private double processingTime;

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