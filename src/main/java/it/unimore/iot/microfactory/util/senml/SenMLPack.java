package it.unimore.iot.microfactory.util.senml;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

public class SenMLPack {

    @JsonValue
    private final List<SenMLRecord> records;

    public SenMLPack() {
        this.records = new ArrayList<>();
    }

    public List<SenMLRecord> getRecords() {
        return records;
    }

    public void addRecord(SenMLRecord record) {
        this.records.add(record);
    }
}