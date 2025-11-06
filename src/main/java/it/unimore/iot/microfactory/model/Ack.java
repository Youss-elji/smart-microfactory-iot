package it.unimore.iot.microfactory.model;

/**
 * Rappresenta un messaggio di riscontro (acknowledgment) inviato da un dispositivo
 * in risposta a un comando ricevuto.
 * Questo POJO viene utilizzato per confermare se un'operazione è stata
 * accettata o ha generato un errore.
 */
public class Ack {

    /**
     * Il tipo di comando a cui questo riscontro si riferisce (es. "START", "RESET").
     */
    private String cmdType;

    /**
     * Lo stato del riscontro, tipicamente "OK" o "ERROR".
     */
    private String status;

    /**
     * Un messaggio testuale opzionale che fornisce dettagli aggiuntivi sul riscontro.
     */
    private String message;

    /**
     * Il timestamp UNIX (in millisecondi) in cui il riscontro è stato generato.
     */
    private long ts;

    /**
     * Identificativo del messaggio correlato, restituito al client per consentire l'abbinamento.
     */
    private String msgId;

    public Ack() {
    }

    public Ack(String cmdType, String status, String message, long ts) {
        this(cmdType, status, message, ts, null);
    }

    public Ack(String cmdType, String status, String message, long ts, String msgId) {
        this.cmdType = cmdType;
        this.status = status;
        this.message = message;
        this.ts = ts;
        this.msgId = msgId;
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

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }
}
