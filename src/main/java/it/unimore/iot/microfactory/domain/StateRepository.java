package it.unimore.iot.microfactory.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.communication.mqtt.CommandPublisher;
import it.unimore.iot.microfactory.model.Command;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Repository centralizzato che mantiene lo stato dei dispositivi e inoltra i comandi MQTT
public class StateRepository {

    private static final Logger logger = LoggerFactory.getLogger(StateRepository.class);

    private static StateRepository instance;
    private final Map<String, Object> states;
    private final Map<String, List<Consumer<Object>>> listeners;
    private final ObjectMapper objectMapper;
    private volatile CommandPublisher commandPublisher;

    // Costruttore privato che inizializza le strutture dati concorrenti per stati e listener
    private StateRepository() {
        this.states = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    // Ritorna l'unica istanza condivisa del repository creando l'oggetto alla prima richiesta
    public static synchronized StateRepository getInstance() {
        if (instance == null) {
            instance = new StateRepository();
        }
        return instance;
    }

    // Registra il publisher MQTT che verrà utilizzato per inoltrare i comandi verso il broker
    public void registerCommandPublisher(CommandPublisher commandPublisher) {
        this.commandPublisher = commandPublisher;
        logger.info("CommandPublisher registered in StateRepository");
    }

    // Inserisce o aggiorna lo stato di un dispositivo identificato da cella, tipo e id
    public void upsert(String cell, String type, String id, Object stateObj) {
        String key = buildKey(cell, type, id);
        this.states.put(key, stateObj);
        logger.debug("State updated for key '{}': {}", key, stateObj);
        notifyListeners(key, stateObj);
    }

    // Recupera lo stato corrente di un dispositivo se presente nel repository
    public Optional<Object> get(String cell, String type, String id) {
        String key = buildKey(cell, type, id);
        return Optional.ofNullable(this.states.get(key));
    }

    // Restituisce tutti gli stati relativi a una cella produttiva specifica
    public Map<String, Object> listByCell(String cell) {
        Map<String, Object> cellStates = new HashMap<>();
        this.states.forEach((key, value) -> {
            if (key.startsWith(cell + "/")) {
                cellStates.put(key, value);
            }
        });
        return cellStates;
    }

    // Associa un listener agli aggiornamenti di stato di un singolo dispositivo
    public void addListener(String cell, String type, String id, Consumer<Object> listener) {
        String key = buildKey(cell, type, id);
        this.listeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
        logger.info("Listener added for key '{}'", key);
    }

    // Notifica tutti i listener registrati per una determinata chiave di stato
    private void notifyListeners(String key, Object stateObj) {
        List<Consumer<Object>> keyListeners = this.listeners.get(key);
        if (keyListeners != null && !keyListeners.isEmpty()) {
            logger.info("Notifying {} listener(s) for key '{}'", keyListeners.size(), key);
            keyListeners.forEach(listener -> {
                try {
                    listener.accept(stateObj);
                } catch (Exception e) {
                    logger.error("Error notifying listener for key '{}'", key, e);
                }
            });
        }
    }

    // Genera la chiave unica che rappresenta un dispositivo all'interno del repository
    private String buildKey(String cell, String type, String id) {
        return String.format("%s/%s/%s", cell, type, id);
    }

    // --- Metodi di supporto per l'esposizione tramite API CoAP ---

    // Restituisce in formato JSON l'elenco dei dispositivi registrati per una cella
    public String listDevicesJson(String cell) {
        try {
            Map<String, Object> cellDevices = listByCell(cell);
            List<Map<String, String>> deviceList = cellDevices.keySet().stream().map(key -> {
                String[] parts = key.split("/");
                return Map.of("type", parts[1], "id", parts[2]);
            }).collect(Collectors.toList());

            Map<String, Object> responsePayload = Map.of(
                    "cell", cell,
                    "devices", deviceList
            );
            return objectMapper.writeValueAsString(responsePayload);
        } catch (Exception e) {
            logger.error("Error serializing device list for cell {}", cell, e);
            return "{\"error\":\"Internal Server Error\"}";
        }
    }

    // Produce la rappresentazione JSON dello stato di un singolo dispositivo se disponibile
    public String getStateJson(String cell, String type, String id) {
        try {
            Optional<Object> state = get(cell, type, id);
            if (state.isPresent()) {
                return objectMapper.writeValueAsString(state.get());
            } else {
                return String.format("{\"error\":\"State for %s/%s/%s not found\"}", cell, type, id);
            }
        } catch (Exception e) {
            logger.error("Error serializing state for {}/{}/{}", cell, type, id, e);
            return "{\"error\":\"Internal Server Error\"}";
        }

    }

    /**
     * Pubblica un comando globale destinato a tutti i dispositivi della fabbrica.
     * @param command Oggetto comando contenente tipo e timestamp del messaggio.
     */
    public boolean publishGlobalCommand(Command command) {
        String type = command != null ? command.getType() : null;
        logger.info("Global factory command received: {}", type);
        if (!ensurePublisherAvailable() || type == null || type.isBlank()) {
            logger.error("Cannot publish global command: missing type");
            return false;
        }

        Command normalized = normalizeCommand(command);
        try {
            commandPublisher.publishGlobalCommand(normalized);
            return true;
        } catch (MqttException e) {
            logger.error("Error publishing global command {}", normalized.getType(), e);
            return false;
        }
    }

    /**
     * Pubblica un comando destinato a un singolo dispositivo identificato da cella, tipo e id.
     * @param cell Identificativo della cella industriale destinataria.
     * @param type Tipo di dispositivo (es. robot, conveyor, quality).
     * @param id Identificativo univoco del dispositivo nella cella.
     * @param command Comando da eseguire con tipo e timestamp.
     */
    public boolean publishCommand(String cell, String type, String id, Command command) {
        String cmdType = command != null ? command.getType() : null;
        logger.info("Publishing command '{}' to device {}/{}/{}", cmdType, cell, type, id);

        if (!ensurePublisherAvailable() || cmdType == null || cmdType.isBlank()) {
            logger.error("Cannot publish command to {}/{}/{}: missing type", cell, type, id);
            return false;
        }

        Command normalized = normalizeCommand(command);
        try {
            commandPublisher.publishDeviceCommand(cell, type, id, normalized);
            return true;
        } catch (MqttException e) {
            logger.error("Error publishing command {} to device {}/{}/{}", normalized.getType(), cell, type, id, e);
            return false;
        }
    }

    // Verifica che il publisher MQTT sia stato registrato prima di inviare nuovi comandi
    private boolean ensurePublisherAvailable() {
        if (commandPublisher == null) {
            logger.error("CommandPublisher not registered. Cannot forward commands to MQTT broker.");
            return false;
        }
        return true;
    }

    // Normalizza il comando assicurando maiuscole e timestamp valorizzato
    private Command normalizeCommand(Command command) {
        Command result = command != null ? command : new Command();
        if (result.getType() != null) {
            result.setType(result.getType().trim().toUpperCase());
        }
        if (result.getTs() <= 0) {
            result.setTs(System.currentTimeMillis());
        }
        return result;
    }
}