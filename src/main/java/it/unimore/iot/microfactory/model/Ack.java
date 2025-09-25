package it.unimore.iot.microfactory.model;

public class Ack {
    private String cmdType;
    private String status;  // OK|ERROR
    private String message; // breve spiegazione
    private long ts;

    public Ack() {
    }

    public Ack(String cmdType, String status, String message, long ts) {
        this.cmdType = cmdType;
        this.status = status;
        this.message = message;
        this.ts = ts;
    }

    public String getCmdType() {
        return cmdType;
    }

    public void setCmdType(String cmdType) {
        this.cmdType = cmdType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }
}