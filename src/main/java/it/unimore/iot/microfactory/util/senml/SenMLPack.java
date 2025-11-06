package it.unimore.iot.microfactory.util.senml;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

// Rappresenta un pacchetto SenML composto da più record serializzati come array JSON
public class SenMLPack {

    @JsonValue
    private final List<SenMLRecord> records;

    // Inizializza un pacchetto vuoto pronto ad accogliere record SenML
    public SenMLPack() {
        this.records = new ArrayList<>();
    }

    // Restituisce l'elenco di record contenuti nel pacchetto
    public List<SenMLRecord> getRecords() {
        return records;
    }

    // Aggiunge un record SenML alla lista da serializzare
    public void addRecord(SenMLRecord record) {
        this.records.add(record);
    }
}