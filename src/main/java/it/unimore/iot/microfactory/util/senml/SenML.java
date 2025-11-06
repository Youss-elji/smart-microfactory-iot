package it.unimore.iot.microfactory.util.senml;

// Utility per creare pacchetti SenML pronti per essere serializzati in JSON
public class SenML {

    // Genera un pacchetto SenML contenente una misura di temperatura in gradi Celsius
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

    // Genera un pacchetto SenML con un valore booleano associato a un nome di misura
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

    // Genera un pacchetto SenML con un valore numerico e l'unità corrispondente
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