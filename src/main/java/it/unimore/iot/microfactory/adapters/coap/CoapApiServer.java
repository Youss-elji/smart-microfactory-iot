package it.unimore.iot.microfactory.adapters.coap;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.domain.StateRepository;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.util.coap.ContentFormat;
import it.unimore.iot.microfactory.util.senml.SenML;
import it.unimore.iot.microfactory.util.senml.SenMLPack;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapApiServer {

    private static final Logger log = LoggerFactory.getLogger(CoapApiServer.class);
    private final CoapServer server;

    public CoapApiServer(StateRepository repo) {
        this(repo, 5683); // Delegate to the constructor with default port
    }

    public CoapApiServer(StateRepository repo, int port) {
        // Use a null config to use defaults, and null address to bind to all interfaces
        this.server = new CoapServer(null, port);
        registerResources(repo);
    }

    private void registerResources(StateRepository repo) {
        server.add(new FactoryResource(repo));
        log.info("Registered {} top-level CoAP resources.", server.getRoot().getChildren().size());
    }

    public void start() {
        try {
            log.info("Starting CoAP server...");
            server.start();
            server.getEndpoints().forEach(endpoint -> {
                log.info("CoAP server listening on: {}:{}", endpoint.getAddress().getHostString(), endpoint.getAddress().getPort());
            });
        } catch (Exception e) {
            log.error("CRITICAL ERROR: Failed to start CoAP server", e);
            throw new RuntimeException("Failed to start CoAP server", e);
        }
    }

    public void stop() {
        log.info("Stopping CoAP server...");
        server.stop();
        server.destroy();
        log.info("CoAP server stopped.");
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
            getAttributes().addContentType(ContentFormat.APPLICATION_SENML_JSON);
            getAttributes().addContentType(ContentFormat.TEXT_PLAIN);
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);

            String resourceType;
            if (type.equals("robot") || type.equals("conveyor")) {
                resourceType = "it.unimore.device.actuator.task";
                getAttributes().addInterfaceDescription("core.a");
            } else {
                resourceType = "it.unimore.device.sensor.capsule";
                getAttributes().addInterfaceDescription("core.s");
            }
            getAttributes().addResourceType(resourceType);

            repo.addListener(cellId, type, id, (newState) -> changed());
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            repo.get(cellId, deviceType, deviceId).ifPresentOrElse(
                state -> {
                    int accept = exchange.getRequestOptions().getAccept();
                    if (accept == -1 || accept == ContentFormat.APPLICATION_JSON) { // Default to JSON
                        handleJsonRequest(exchange, state);
                    } else if (accept == ContentFormat.APPLICATION_SENML_JSON) {
                        handleSenMLRequest(exchange, state);
                    } else if (accept == ContentFormat.TEXT_PLAIN) {
                        handleTextRequest(exchange, state);
                    } else {
                        exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE);
                    }
                },
                () -> exchange.respond(CoAP.ResponseCode.NOT_FOUND)
            );
        }

        private void handleJsonRequest(CoapExchange exchange, Object state) {
            try {
                String json = new ObjectMapper().writeValueAsString(state);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, ContentFormat.APPLICATION_JSON);
            } catch (Exception e) {
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        private void handleTextRequest(CoapExchange exchange, Object state) {
            String response;
            if (state instanceof RobotCellStatus) {
                response = ((RobotCellStatus) state).getStatus().toString();
            } else {
                response = state.toString();
            }
            exchange.respond(CoAP.ResponseCode.CONTENT, response, ContentFormat.TEXT_PLAIN);
        }

        private void handleSenMLRequest(CoapExchange exchange, Object state) {
            try {
                String baseName = String.format("%s/%s/%s/", cellId, deviceType, deviceId);
                SenMLPack pack;
                if (state instanceof RobotCellStatus) {
                    RobotCellStatus s = (RobotCellStatus) state;
                    pack = SenML.fromNumeric(baseName, "status", s.getStatus().ordinal(), "state", s.getTimestamp());
                } else {
                    pack = SenML.fromNumeric(baseName, "value", 0, "N/A", System.currentTimeMillis());
                }
                String json = new ObjectMapper().writeValueAsString(pack);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, ContentFormat.APPLICATION_SENML_JSON);
            } catch (Exception e) {
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }
    }
}