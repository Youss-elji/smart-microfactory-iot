package it.unimore.iot.microfactory.adapters.coap;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.domain.StateRepository;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CoapApiServer {

    private static final Logger log = LoggerFactory.getLogger(CoapApiServer.class);
    private final CoapServer server;

    public CoapApiServer(StateRepository repo) {
        // Create a configuration that does not load from file
        Configuration config = Configuration.createStandardWithoutFile();
        this.server = new CoapServer(config);
        registerResources(repo);
    }

    private void registerResources(StateRepository repo) {
        server.add(new FactoryResource(repo));
    }

    public void start() {
        log.info("Starting CoAP server on UDP/5683 (default)...");
        server.start();
        log.info("CoAP server started.");
    }

    public void stop() {
        log.info("Stopping CoAP server...");
        server.stop();
        server.destroy();
        log.info("CoAP server stopped.");
    }

    private static String extractVar(CoapExchange ex, int pos) {
        String[] parts = ex.getRequestOptions().getUriPath().toArray(new String[0]);
        return (pos > 0 && pos < parts.length) ? parts[pos] : "";
    }

    // --- Dynamic Resource Definitions ---

    static class FactoryResource extends CoapResource {
        private final StateRepository repo;

        FactoryResource(StateRepository repo) {
            super("factory");
            this.repo = repo;
            getAttributes().setTitle("Factory Resource");
            getAttributes().addResourceType("factory");
            getAttributes().addInterfaceDescription("core.ll");
        }

        @Override
        public Resource getChild(String name) {
            return new CellResource(name, repo);
        }
    }

    static class CellResource extends CoapResource {
        private final StateRepository repo;
        private final String cellId;

        CellResource(String name, StateRepository repo) {
            super(name);
            this.cellId = name;
            this.repo = repo;
            getAttributes().setTitle("Cell " + name);
            getAttributes().addResourceType("cell");
            getAttributes().addInterfaceDescription("core.ll");
            add(new DevicesResource("devices", cellId, repo));
        }

        @Override
        public Resource getChild(String name) {
            Resource child = super.getChild(name);
            if (child != null) {
                return child;
            }
            return new DeviceTypeResource(name, cellId, repo);
        }
    }

    static class DevicesResource extends CoapResource {
        private final StateRepository repo;
        private final String cellId;

        DevicesResource(String name, String cellId, StateRepository repo) {
            super(name);
            this.repo = repo;
            this.cellId = cellId;
            getAttributes().setTitle("Device List");
            getAttributes().addResourceType("devices");
            getAttributes().addInterfaceDescription("core.r");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            String json = repo.listDevicesJson(cellId);
            exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_JSON);
        }
    }

    static class DeviceTypeResource extends CoapResource {
        private final StateRepository repo;
        private final String cellId;
        private final String type;

        DeviceTypeResource(String name, String cellId, StateRepository repo) {
            super(name);
            this.type = name;
            this.cellId = cellId;
            this.repo = repo;
            getAttributes().setTitle("Device Type " + name);
        }

        @Override
        public Resource getChild(String name) {
            return new DeviceIdResource(name, cellId, type, repo);
        }
    }

    static class DeviceIdResource extends CoapResource {
        DeviceIdResource(String name, String cellId, String type, StateRepository repo) {
            super(name);
            getAttributes().setTitle("Device " + name);
            add(new DeviceStateResource("state", cellId, type, name, repo));
        }
    }

    static class DeviceStateResource extends CoapResource {
        private final StateRepository repo;
        private final String cellId;
        private final String deviceType;
        private final String deviceId;

        DeviceStateResource(String name, String cellId, String type, String id, StateRepository repo) {
            super(name);
            this.repo = repo;
            this.cellId = cellId;
            this.deviceType = type;
            this.deviceId = id;

            setObservable(true);
            getAttributes().setObservable();
            getAttributes().addInterfaceDescription("core.s");
            getAttributes().addResourceType(String.format("%s-state", type));

            repo.addListener(cellId, type, id, (newState) -> changed());
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            repo.get(cellId, deviceType, deviceId).ifPresentOrElse(
                state -> {
                    try {
                        String json = new ObjectMapper().writeValueAsString(state);
                        exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_JSON);
                    } catch (Exception e) {
                        exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
                    }
                },
                () -> exchange.respond(CoAP.ResponseCode.NOT_FOUND)
            );
        }
    }
}