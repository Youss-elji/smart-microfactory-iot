package it.unimore.iot.microfactory.adapters.coap;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.domain.StateRepository;
import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
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
import org.eclipse.californium.elements.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Gestisce il server CoAP per la microfactory intelligente.
 * Questo server espone un'API REST-like per monitorare e controllare
 * i dispositivi della fabbrica tramite il protocollo CoAP.
 * L'architettura è basata su risorse CoAP nidificate dinamicamente,
 * che permettono di navigare la gerarchia della fabbrica: /factory/{cellId}/{deviceType}/{deviceId}/...
 */
public class CoapApiServer {

    private static final Logger log = LoggerFactory.getLogger(CoapApiServer.class);
    private static final Set<String> SUPPORTED_DEVICE_COMMANDS = Set.of("RESET", "START", "STOP");
    private static final Set<String> SUPPORTED_GLOBAL_COMMANDS = Set.of("RESET", "START", "STOP", "EMERGENCY");
    private final CoapServer server;

    /**
     * Inizializza il server sulla porta CoAP di default (5683).
     *
     * @param repo Il repository dello stato condiviso per accedere ai dati dei dispositivi.
     */
    public CoapApiServer(StateRepository repo) {
        this(repo, 5683);
    }

    /**
     * Inizializza il server sulla porta specificata.
     *
     * @param repo Il repository dello stato condiviso per accedere ai dati dei dispositivi.
     * @param port La porta di ascolto per il server CoAP.
     */
    public CoapApiServer(StateRepository repo, int port) {
        // Configurazione degli endpoint con parametri di rete ottimizzati per ambienti IoT.
        Configuration cfg = Configuration.createStandardWithoutFile();
        this.server = new CoapServer(cfg, port);
        registerResources(repo);
        log.info("Risorse CoAP di primo livello registrate: {}", server.getRoot().getChildren().size());
    }

    /**
     * Registra le risorse CoAP di primo livello sul server.
     *
     * @param repo Il repository dello stato da passare alle risorse.
     */
    private void registerResources(StateRepository repo) {
        server.add(new FactoryResource(repo));
    }

    /**
     * Avvia il server CoAP e si mette in ascolto sulla porta configurata.
     */
    public void start() {
        try {
            log.info("Avvio del server CoAP...");
            server.start();
            server.getEndpoints().forEach(ep ->
                    log.info("Server CoAP in ascolto su {}:{}", ep.getAddress().getHostString(), ep.getAddress().getPort())
            );
        } catch (Exception e) {
            log.error("ERRORE CRITICO: Impossibile avviare il server CoAP", e);
            throw new RuntimeException("Impossibile avviare il server CoAP", e);
        }
    }

    /**
     * Ferma il server CoAP e rilascia le risorse.
     */
    public void stop() {
        log.info("Arresto del server CoAP...");
        server.stop();
        server.destroy();
        log.info("Server CoAP arrestato.");
    }

    /**
     * Risorsa radice che rappresenta l'intera fabbrica.
     * Espone l'endpoint `/factory`.
     * GET: Ritorna uno stato generale della fabbrica.
     * FIGLI DINAMICI: Instrada le richieste per `{cellId}` alla `CellResource` corrispondente.
     */
    static class FactoryResource extends CoapResource {
        private final StateRepository repo;

        FactoryResource(StateRepository repo) {
            super("factory");
            this.repo = repo;
            getAttributes().setTitle("Factory Resource");
            getAttributes().addResourceType("factory");
            getAttributes().addInterfaceDescription("core.ll");
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);

            // Aggiunge la risorsa per i comandi globali
            add(new GlobalCommandResource("cmd", repo));
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            String summary = "{\"name\":\"smart-microfactory\",\"status\":\"operational\",\"version\":\"1.0\"}";
            exchange.respond(CoAP.ResponseCode.CONTENT, summary, MediaTypeRegistry.APPLICATION_JSON);
        }

        /**
         * Gestisce il routing dinamico per le celle.
         * Se la risorsa richiesta non è statica (es. 'cmd'), la interpreta come un ID di cella.
         */
        @Override
        public Resource getChild(String name) {
            Resource existing = super.getChild(name);
            if (existing != null) {
                return existing;
            }
            // Se non è una risorsa fissa, la consideriamo una cella dinamica
            return new CellResource(name, repo);
        }
    }

    /**
     * Risorsa che rappresenta una singola cella produttiva.
     * Espone l'endpoint `/factory/{cellId}`.
     * FIGLI DINAMICI: Instrada le richieste per `{deviceType}` alla `DeviceTypeResource`.
     */
    static class CellResource extends CoapResource {
        private final StateRepository repo;

        CellResource(String name, StateRepository repo) {
            super(name);
            this.repo = repo;
            getAttributes().setTitle("Cell " + name);
            getAttributes().addResourceType("cell");
            getAttributes().addInterfaceDescription("core.ll");

            // Aggiunge la risorsa fissa 'devices' per elencare i dispositivi della cella
            add(new DevicesResource("devices", name, repo));
        }

        /**
         * Gestisce il routing dinamico per i tipi di dispositivo.
         */
        @Override
        public Resource getChild(String name) {
            Resource child = super.getChild(name);
            if (child != null) {
                return child;
            }
            // Se non è 'devices', la consideriamo un tipo di dispositivo dinamico
            return new DeviceTypeResource(name, getName(), repo);
        }
    }

    /**
     * Risorsa per elencare tutti i dispositivi di una cella.
     * Espone l'endpoint `/factory/{cellId}/devices`.
     * GET: Ritorna la lista in formato JSON di tutti i dispositivi registrati nella cella.
     */
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
                log.error("Errore durante l'elenco dei dispositivi per la cella {}", cellId, e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR, "Errore nell'elenco dispositivi");
            }
        }
    }

    /**
     * Risorsa che rappresenta una categoria di dispositivi (es. 'robot').
     * Espone l'endpoint `/factory/{cellId}/{deviceType}`.
     * FIGLI DINAMICI: Instrada le richieste per `{deviceId}` alla `DeviceIdResource`.
     */
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

        /**
         * Gestisce il routing dinamico per gli ID di dispositivo.
         */
        @Override
        public Resource getChild(String name) {
            return new DeviceIdResource(name, cellId, type, repo);
        }
    }

    /**
     * Risorsa che rappresenta un singolo dispositivo.
     * Espone l'endpoint `/factory/{cellId}/{deviceType}/{deviceId}`.
     * Questa risorsa agisce come un contenitore per le sotto-risorse 'state' e 'cmd'.
     */
    static class DeviceIdResource extends CoapResource {
        DeviceIdResource(String name, String cellId, String type, StateRepository repo) {
            super(name);
            getAttributes().setTitle("Device " + name);
            getAttributes().addResourceType("it.unimore.device." + type);
            getAttributes().addInterfaceDescription("core.ll");

            DeviceStateResource stateResource = new DeviceStateResource("state", cellId, type, name, repo);
            add(stateResource);
            add(new DeviceCommandResource("cmd", cellId, type, name, repo, stateResource));
        }
    }

    /**
     * Gestisce lo stato real-time dei dispositivi.
     * Espone l'endpoint `.../{deviceId}/state`.
     * GET: Recupera lo stato attuale del dispositivo. Supporta content negotiation per JSON, SenML+JSON e Text-Plain.
     * OBSERVABLE: Supporta la modalità Observe per ricevere notifiche push sui cambiamenti di stato.
     */
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
            // Aggiunge un listener per notificare i client CoAP quando lo stato cambia
            repo.addListener(cellId, type, id, newState -> changed());
        }

        /**
         * Processa la richiesta GET per recuperare lo stato del dispositivo.
         * La risposta varia in base all'header 'Accept' della richiesta (content negotiation).
         *
         * @param exchange Il contesto della richiesta CoAP.
         */
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
                    () -> exchange.respond(CoAP.ResponseCode.NOT_FOUND, "Dispositivo non trovato")
            );
        }

        private void handleJsonRequest(CoapExchange exchange, Object state) {
            try {
                String json = new ObjectMapper().writeValueAsString(state);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                log.error("Errore durante la serializzazione JSON per {}", deviceId, e);
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
            exchange.respond(CoAP.ResponseCode.CONTENT, response, MediaTypeRegistry.TEXT_PLAIN);
        }

        private void handleSenMLRequest(CoapExchange exchange, Object state) {
            try {
                String baseName = "%s/%s/%s/".formatted(cellId, deviceType, deviceId);
                SenMLPack pack;
                if (state instanceof RobotCellStatus s) {
                    pack = SenML.fromNumeric(baseName, "status",
                            s.getStatus().ordinal(), "state", s.getTimestamp());
                } else {
                    // Fallback per stati non noti
                    pack = SenML.fromNumeric(baseName, "value",
                            0, "N/A", System.currentTimeMillis());
                }
                String json = new ObjectMapper().writeValueAsString(pack);
                exchange.respond(CoAP.ResponseCode.CONTENT, json, MediaTypeRegistry.APPLICATION_SENML_JSON);
            } catch (Exception e) {
                log.error("Errore durante la serializzazione SenML per {}", deviceId, e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Endpoint per inviare comandi globali a tutta la fabbrica.
     * Espone l'endpoint `/factory/cmd`.
     * POST: Esegue un comando su tutti i dispositivi (es. RESET, START, STOP).
     * GET: Restituisce la lista dei comandi supportati.
     */
    static class GlobalCommandResource extends CoapResource {
        private final StateRepository repo;
        private final ObjectMapper mapper = new ObjectMapper();

        GlobalCommandResource(String name, StateRepository repo) {
            super(name);
            this.repo = repo;
            getAttributes().setTitle("Factory Global Command");
            getAttributes().addResourceType("it.unimore.factory.command");
            getAttributes().addInterfaceDescription("core.a");
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
            try {
                int ct = exchange.getRequestOptions().getContentFormat();
                log.debug("Ricevuto POST su /factory/cmd con Content-Format: {}", ct);

                if (ct != -1 && ct != MediaTypeRegistry.APPLICATION_JSON) {
                    log.warn("Content-Format non supportato: {} ({})", ct, MediaTypeRegistry.toString(ct));
                    exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE,
                            "Supportato solo application/json con payload Command");
                    return;
                }

                Command command = extractCommand(exchange);
                if (command == null || command.getType() == null || command.getType().isBlank()) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
                            "Campo 'type' mancante nel payload Command");
                    return;
                }

                command.setType(command.getType().trim().toUpperCase());
                if (!SUPPORTED_GLOBAL_COMMANDS.contains(command.getType())) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
                            "Comando non supportato. Valori ammessi: " + SUPPORTED_GLOBAL_COMMANDS);
                    return;
                }

                log.info("COAP CMD GLOBALE -> cmd={}", command.getType());

                boolean forwarded = repo.publishGlobalCommand(command);
                if (!forwarded) {
                    exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
                            "Impossibile inoltrare il comando al broker MQTT");
                    return;
                }

                Ack ack = new Ack(command.getType(), "ACCEPTED",
                        "Comando globale inoltrato al broker MQTT", System.currentTimeMillis(),
                        command.getMsgId());
                String body = mapper.writeValueAsString(ack);
                exchange.respond(CoAP.ResponseCode.CHANGED, body, MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                log.error("Errore durante la gestione di POST /factory/cmd", e);
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Richiesta non valida: " + e.getMessage());
            }
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            try {
                var body = mapper.createObjectNode();
                var supported = mapper.createArrayNode();
                SUPPORTED_GLOBAL_COMMANDS.forEach(supported::add);
                body.set("supported", supported);

                var example = mapper.createObjectNode();
                example.put("type", "RESET");
                example.put("ts", System.currentTimeMillis());
                body.set("payloadExample", example);

                exchange.respond(CoAP.ResponseCode.CONTENT, mapper.writeValueAsString(body),
                        MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                log.error("Errore durante la serializzazione della risposta GET /factory/cmd", e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR, "Errore di serializzazione");
            }
        }

        private Command extractCommand(CoapExchange exchange) throws Exception {
            byte[] payload = exchange.getRequestPayload();
            if (payload == null || payload.length == 0) {
                return null;
            }
            return mapper.readValue(payload, Command.class);
        }
    }

    /**
     * Endpoint per inviare comandi a un dispositivo specifico.
     * Espone l'endpoint `.../{deviceId}/cmd`.
     * POST: Esegue un comando sul dispositivo (es. START, STOP). Comandi supportati: START, STOP, RESET.
     * GET: Restituisce la lista dei comandi supportati dal dispositivo.
     */
    static class DeviceCommandResource extends CoapResource {
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
            getAttributes().addContentType(MediaTypeRegistry.APPLICATION_JSON);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
            try {
                int ct = exchange.getRequestOptions().getContentFormat();
                log.debug("Ricevuto POST su /cmd per {} con Content-Format: {}", deviceId, ct);

                if (ct != -1 && ct != MediaTypeRegistry.APPLICATION_JSON) {
                    log.warn("Content-Format non supportato: {} per il dispositivo {}/{}/{}", ct, cellId, deviceType, deviceId);
                    exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE,
                            "Supportato solo application/json con payload Command");
                    return;
                }

                Command command = extractCommand(exchange);
                if (command == null || command.getType() == null || command.getType().isBlank()) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Campo 'type' mancante nel payload Command");
                    return;
                }

                command.setType(command.getType().trim().toUpperCase());
                if (!SUPPORTED_DEVICE_COMMANDS.contains(command.getType())) {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
                            "Comando non supportato. Valori ammessi: " + SUPPORTED_DEVICE_COMMANDS);
                    return;
                }

                log.info("COAP CMD -> cell={}, type={}, id={}, cmd={}", cellId, deviceType, deviceId, command.getType());

                // Pubblica il comando per il dispositivo specifico
                boolean forwarded = repo.publishCommand(cellId, deviceType, deviceId, command);
                if (!forwarded) {
                    exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
                            "Impossibile inoltrare il comando al broker MQTT");
                    return;
                }

                // Notifica un cambiamento di stato per aggiornare i client in observe
                if (stateResource != null) {
                    stateResource.changed();
                }

                Ack ack = new Ack(command.getType(), "ACCEPTED",
                        "Comando inoltrato al broker MQTT", System.currentTimeMillis(),
                        command.getMsgId());
                String body = mapper.writeValueAsString(ack);
                exchange.respond(CoAP.ResponseCode.CHANGED, body, MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                log.error("Errore durante la gestione di POST /cmd per {}", deviceId, e);
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Richiesta non valida: " + e.getMessage());
            }
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            try {
                var body = mapper.createObjectNode();
                var supported = mapper.createArrayNode();
                SUPPORTED_DEVICE_COMMANDS.forEach(supported::add);
                body.set("supported", supported);

                var example = mapper.createObjectNode();
                example.put("type", "START");
                example.put("ts", System.currentTimeMillis());
                body.set("payloadExample", example);

                exchange.respond(CoAP.ResponseCode.CONTENT, mapper.writeValueAsString(body),
                        MediaTypeRegistry.APPLICATION_JSON);
            } catch (Exception e) {
                log.error("Errore durante la serializzazione della risposta GET /cmd per {}", deviceId, e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR, "Errore di serializzazione");
            }
        }

        private Command extractCommand(CoapExchange exchange) throws Exception {
            byte[] payload = exchange.getRequestPayload();
            if (payload == null || payload.length == 0) {
                return null;
            }
            return mapper.readValue(payload, Command.class);
        }
    }
}