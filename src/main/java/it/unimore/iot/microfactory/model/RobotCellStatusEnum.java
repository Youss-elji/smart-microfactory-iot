package it.unimore.iot.microfactory.model;

/**
 * Definisce i possibili stati operativi per una cella robotica.
 */
public enum RobotCellStatusEnum {
    /**
     * Il robot è inattivo e in attesa di un nuovo compito.
     */
    IDLE,
    /**
     * Il robot sta eseguendo un ciclo di lavorazione.
     */
    PROCESSING,
    /**
     * Il robot si trova in uno stato di allarme o errore che richiede intervento.
     */
    ALARM
}