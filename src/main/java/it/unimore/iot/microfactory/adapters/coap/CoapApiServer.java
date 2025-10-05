package it.unimore.iot.microfactory.adapters.coap;

import com.fasterxml.jackson.databind.JsonNode;
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

import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.elements.config.TcpConfig;

public class CoapApiServer {

    private static final Logger log = LoggerFactory.getLogger(CoapApiServer.class);
    private final CoapServer server;

    public CoapApiServer(StateRepository repo) {
        this(repo, 5683);
    }

    public CoapApiServer(StateRepository repo, int port) {
        Configuration cfg = Configuration.createStandardWithoutFile();
        UdpConfig.register();
        TcpConfig.register();

        this.server = new CoapServer(cfg, port);
        registerResources(repo);
        log.info("Registered {} top-level CoAP resources.", server.getRoot().getChildren().size());
    }

    private void registerResources(StateRepository repo) {
        server.add(new FactoryResource(repo));
    }

    public void start() {
        try {
            log.info("Starting CoAP server...");
            server.start();
            server.getEndpoints().forEach(ep ->
                    log.info("CoAP server listening on {}:{}", ep.getAddress().getHostString(), ep.getAddress().getPort())
            );
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

    // --- Risorse Dinamiche ---

    static class FactoryResource extends CoapResource {
        private final StateRepository repo;

        FactoryResource(StateRepository repo) {
            super("factory");
            this.repo = repo;
            getAttributes().setTitle("Factory Resource");
            getAttributes().addResourceType("factory");
            getAttributes().addInterfaceDescription("core.ll");
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);

            add(new GlobalCommandResource("cmd", repo));
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            String summary = "{\"name\":\"smart-microfactory\",\"status\":\"operational\",\"version\":\"1.0\"}";
            exchange.respond(CoAP.ResponseCode.CONTENT, summary, MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public Resource getChild(String name) {
            Resource existing = super.getChild(name);
            if (existing != null) return existing;
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
            if (child != null) return child;
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
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            try {
                String json = repo.listDevicesJson(cellId);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR, "Error listing devices");
            }
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
            getAttributes().addResourceType("deviceType");
            getAttributes().addInterfaceDescription("core.ll");
        }

        @Override
        public Resource getChild(String name) {
            return new DeviceIdResource(name, cellId, type, repo);
        }
    }

    static class DeviceIdResource extends CoapResource {
        private final DeviceStateResource stateResource;

        DeviceIdResource(String name, String cellId, String type, StateRepository repo) {
            super(name);
            getAttributes().setTitle("Device " + name);
            getAttributes().addResourceType("it.unimore.device." + type);
            getAttributes().addInterfaceDescription("core.ll");

            this.stateResource = new DeviceStateResource("state", cellId, type, name, repo);
            add(stateResource);
            add(new DeviceCommandResource("cmd", cellId, type, name, repo, stateResource));
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
            setObserveType(CoAP.Type.CON);
            getAttributes().setObservable();

            getAttributes().addContentType(ContentFormat.APPLICATION_SENML_JSON);
            getAttributes().addContentType(ContentFormat.TEXT_PLAIN);
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);

            if ("robot".equals(type) || "conveyor".equals(type)) {
                getAttributes().addResourceType("it.unimore.device.actuator.task");
                getAttributes().addInterfaceDescription("core.a");
            } else {
                getAttributes().addResourceType("it.unimore.device.sensor.capsule");
                getAttributes().addInterfaceDescription("core.s");
            }

            getAttributes().setTitle("State of " + id + " (" + type + ")");
            repo.addListener(cellId, type, id, newState -> changed());
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            repo.get(cellId, deviceType, deviceId).ifPresentOrElse(
                    state -> {
                        int accept = exchange.getRequestOptions().getAccept();
                        if (accept == -1 || accept == MediaTypeRegistry.APPLICATION_JSON) {
                            handleJsonRequest(exchange, state);
                        } else if (accept == MediaTypeRegistry.APPLICATION_SENML_JSON) {
                            handleSenMLRequest(exchange, state);
                        } else if (accept == MediaTypeRegistry.TEXT_PLAIN) {
                            handleTextRequest(exchange, state);
                        } else {
                            exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE);
                        }
                    },
                    () -> exchange.respond(CoAP.ResponseCode.NOT_FOUND, "Device not found")
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
            if (state instanceof RobotCellStatus s) {
                response = s.getStatus().toString();
            } else {
                response = state.toString();
            }
            exchange.respond(CoAP.ResponseCode.CONTENT, response, ContentFormat.TEXT_PLAIN);
        }

        private void handleSenMLRequest(CoapExchange exchange, Object state) {
            try {
                String baseName = "%s/%s/%s/".formatted(cellId, deviceType, deviceId);
                SenMLPack pack;
                if (state instanceof RobotCellStatus s) {
                    pack = SenML.fromNumeric(baseName, "status",
                            s.getStatus().ordinal(), "state", s.getTimestamp());
                } else {
                    pack = SenML.fromNumeric(baseName, "value",
                            0, "N/A", System.currentTimeMillis());
                }
                String json = new ObjectMapper().writeValueAsString(pack);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_SENML_JSON);
            } catch (Exception e) {
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    static class GlobalCommandResource extends CoapResource {
        private static final Logger LOG = LoggerFactory.getLogger(GlobalCommandResource.class);
        private final StateRepository repo;
        private final ObjectMapper mapper = new ObjectMapper();

        GlobalCommandResource(String name, StateRepository repo) {
            super(name);
            this.repo = repo;
            getAttributes().setTitle("Factory Global Command");
            getAttributes().addResourceType("it.unimore.factory.command");
            getAttributes().addInterfaceDescription("core.a");
            getAttributes().addContentType(MediaTypeRegistry.TEXT_PLAIN);
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
            try {
                // CRITICAL: Validate Content-Format
                int ct = exchange.getRequestOptions().getContentFormat();
                LOG.debug("Received POST with Content-Format: {}", ct);

                // Accept only text/plain (0), application/json (50), or unspecified (-1)
                if (ct != -1 && ct != MediaTypeRegistry.TEXT_PLAIN && ct != MediaTypeRegistry.APPLICATION_JSON) {
                    LOG.warn("Rejected unsupported Content-Format: {} ({})",
                            ct, MediaTypeRegistry.toString(ct));
                    exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE,
                            "Only text/plain or application/json supported");
                    return;
                }

                String cmd = extractCommand(exchange, ct);
                if (cmd == null || cmd.isBlank()) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Missing 'cmd'");
                    return;
                }

                cmd = cmd.trim().toUpperCase();
                LOG.info("COAP GLOBAL CMD -> factory-wide, cmd={}", cmd);

                // Publish global command via repository
                repo.publishGlobalCommand(cmd);

                exchange.respond(CoAP.ResponseCode.CHANGED, "Command accepted");
            } catch (Exception e) {
                LOG.error("Error handling POST /factory/cmd", e);
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Invalid request");
            }
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            String body = "{\"supported\":[\"RESET\",\"START\",\"STOP\",\"EMERGENCY\"]}";
            exchange.respond(CoAP.ResponseCode.CONTENT, body, MediaTypeRegistry.APPLICATION_JSON);
        }

        private String extractCommand(CoapExchange exchange, int ct) throws Exception {
            byte[] payload = exchange.getRequestPayload();

            if (ct == MediaTypeRegistry.APPLICATION_JSON) {
                JsonNode node = mapper.readTree(payload == null ? new byte[0] : payload);
                return node.hasNonNull("cmd") ? node.get("cmd").asText() : null;
            } else {
                // Default to text/plain for ct == -1 or ct == 0
                return (payload == null) ? "" : new String(payload);
            }
        }
    }

    static class DeviceCommandResource extends CoapResource {
        private static final Logger LOG = LoggerFactory.getLogger(DeviceCommandResource.class);
        private final StateRepository repo;
        private final String cellId;
        private final String deviceType;
        private final String deviceId;
        private final DeviceStateResource stateResource;
        private final ObjectMapper mapper = new ObjectMapper();

        DeviceCommandResource(String name, String cellId, String type, String id,
                              StateRepository repo, DeviceStateResource stateResource) {
            super(name);
            this.repo = repo;
            this.cellId = cellId;
            this.deviceType = type;
            this.deviceId = id;
            this.stateResource = stateResource;

            getAttributes().setTitle("Device Command");
            getAttributes().addResourceType("it.unimore.device.command");
            getAttributes().addInterfaceDescription("core.a");
            getAttributes().addContentType(MediaTypeRegistry.TEXT_PLAIN);
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
            try {
                // CRITICAL: Validate Content-Format
                int ct = exchange.getRequestOptions().getContentFormat();
                LOG.debug("Received POST with Content-Format: {}", ct);

                // Accept only text/plain (0), application/json (50), or unspecified (-1)
                if (ct != -1 && ct != MediaTypeRegistry.TEXT_PLAIN && ct != MediaTypeRegistry.APPLICATION_JSON) {
                    LOG.warn("Rejected unsupported Content-Format: {} for device {}/{}/{}",
                            ct, cellId, deviceType, deviceId);
                    exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE,
                            "Only text/plain or application/json supported");
                    return;
                }

                String cmd = extractCommand(exchange, ct);
                if (cmd == null || cmd.isBlank()) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Missing 'cmd'");
                    return;
                }

                cmd = cmd.trim().toUpperCase();
                LOG.info("COAP CMD -> cell={}, type={}, id={}, cmd={}", cellId, deviceType, deviceId, cmd);

                // Publish command to specific device via repository
                repo.publishCommand(cellId, deviceType, deviceId, cmd);

                if (stateResource != null) {
                    stateResource.changed();
                }

                exchange.respond(CoAP.ResponseCode.CHANGED);
            } catch (Exception e) {
                LOG.error("Error handling POST /cmd", e);
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Invalid request");
            }
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            String body = "{\"supported\":[\"RESET\",\"START\",\"STOP\"]}";
            exchange.respond(CoAP.ResponseCode.CONTENT, body, MediaTypeRegistry.APPLICATION_JSON);
        }

        private String extractCommand(CoapExchange exchange, int ct) throws Exception {
            byte[] payload = exchange.getRequestPayload();

            if (ct == MediaTypeRegistry.APPLICATION_JSON) {
                JsonNode node = mapper.readTree(payload == null ? new byte[0] : payload);
                return node.hasNonNull("cmd") ? node.get("cmd").asText() : null;
            } else {
                return (payload == null) ? "" : new String(payload);
            }
        }
    }
}