package it.unimore.iot.microfactory.model;

public class Command {
    private String type; // START|STOP|RESET
    private long ts;

    public Command() {
    }

    public Command(String type, long ts) {
        this.type = type;
        this.ts = ts;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }
}