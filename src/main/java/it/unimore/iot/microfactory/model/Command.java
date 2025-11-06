package it.unimore.iot.microfactory.model;

/**
 * Rappresenta un comando inviato a un dispositivo della microfactory.
 * Questo POJO viene serializzato in JSON per essere inviato, ad esempio,
 * tramite MQTT o CoAP.
 */
public class Command {

    /**
     * Il tipo di comando da eseguire (es. "START", "STOP", "RESET").
     */
    private String type;

    /**
     * Il timestamp UNIX (in millisecondi) che indica quando il comando è stato creato.
     */
    private long ts;

    /**
     * Identificativo opzionale del messaggio, utilizzato per correlare comandi e ACK.
     */
    private String msgId;

    public Command() {
    }

    public Command(String type, long ts) {
        this(type, ts, null);
    }

    public Command(String type, long ts, String msgId) {
        this.type = type;
        this.ts = ts;
        this.msgId = msgId;
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

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }
}