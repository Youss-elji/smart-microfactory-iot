package it.unimore.iot.microfactory.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}