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

public class StateRepository {

    private static final Logger logger = LoggerFactory.getLogger(StateRepository.class);

    private static StateRepository instance;
    private final Map<String, Object> states;
    private final Map<String, List<Consumer<Object>>> listeners;
    private final ObjectMapper objectMapper;
    private volatile CommandPublisher commandPublisher;

    private StateRepository() {
        this.states = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized StateRepository getInstance() {
        if (instance == null) {
            instance = new StateRepository();
        }
        return instance;
    }

    public void registerCommandPublisher(CommandPublisher commandPublisher) {
        this.commandPublisher = commandPublisher;
        logger.info("CommandPublisher registered in StateRepository");
    }

    public void upsert(String cell, String type, String id, Object stateObj) {
        String key = buildKey(cell, type, id);
        this.states.put(key, stateObj);
        logger.debug("State updated for key '{}': {}", key, stateObj);
        notifyListeners(key, stateObj);
    }

    public Optional<Object> get(String cell, String type, String id) {
        String key = buildKey(cell, type, id);
        return Optional.ofNullable(this.states.get(key));
    }

    public Map<String, Object> listByCell(String cell) {
        Map<String, Object> cellStates = new HashMap<>();
        this.states.forEach((key, value) -> {
            if (key.startsWith(cell + "/")) {
                cellStates.put(key, value);
            }
        });
        return cellStates;
    }

    public void addListener(String cell, String type, String id, Consumer<Object> listener) {
        String key = buildKey(cell, type, id);
        this.listeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
        logger.info("Listener added for key '{}'", key);
    }

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

    private String buildKey(String cell, String type, String id) {
        return String.format("%s/%s/%s", cell, type, id);
    }

    // --- Stub methods for CoAP API ---

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
     * Publish a global command to all devices in the factory
     * @param cmd Command to execute (e.g., RESET, START, STOP, EMERGENCY)
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
     * Publish a command to a specific device
     * @param cell Cell ID
     * @param type Device type (robot, conveyor, quality)
     * @param id Device ID
     * @param command Command to execute (e.g., RESET, START, STOP)
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

    private boolean ensurePublisherAvailable() {
        if (commandPublisher == null) {
            logger.error("CommandPublisher not registered. Cannot forward commands to MQTT broker.");
            return false;
        }
        return true;
    }

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