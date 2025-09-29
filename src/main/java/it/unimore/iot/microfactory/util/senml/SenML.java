package it.unimore.iot.microfactory.util.senml;

public class SenML {

    public static SenMLPack fromTemperature(String baseName, double value, long epochSeconds) {
        SenMLPack pack = new SenMLPack();
        SenMLRecord record = new SenMLRecord();
        record.setBaseName(baseName);
        record.setUnit("Cel");
        record.setValue(value);
        record.setTime(epochSeconds);
        pack.addRecord(record);
        return pack;
    }

    public static SenMLPack fromBoolean(String baseName, String name, boolean value, long epochSeconds) {
        SenMLPack pack = new SenMLPack();
        SenMLRecord record = new SenMLRecord();
        record.setBaseName(baseName);
        record.setName(name);
        record.setBooleanValue(value);
        record.setTime(epochSeconds);
        pack.addRecord(record);
        return pack;
    }

    public static SenMLPack fromNumeric(String baseName, String name, double value, String unit, long epochSeconds) {
        SenMLPack pack = new SenMLPack();
        SenMLRecord record = new SenMLRecord();
        record.setBaseName(baseName);
        record.setName(name);
        record.setUnit(unit);
        record.setValue(value);
        record.setTime(epochSeconds);
        pack.addRecord(record);
        return pack;
    }
}