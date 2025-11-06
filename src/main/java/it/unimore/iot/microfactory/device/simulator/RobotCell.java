package it.unimore.iot.microfactory.device.simulator;

import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.RobotCellStatus;
import it.unimore.iot.microfactory.model.RobotCellStatusEnum;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

// Simulatore di cella robotica che alterna stati operativi e gestisce comandi MQTT
public class RobotCell extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(RobotCell.class);

    private static final int IDLE_DURATION_MS = 2000;
    private static final int MAX_PROCESSING_DURATION_MS = 5000;
    private static final double ALARM_PROBABILITY = 0.1;

    private final Random random = new Random();
    private RobotCellStatusEnum currentState = RobotCellStatusEnum.IDLE;

    private final String statusTopic;
    private final String cmdTopic;
    private final String ackTopic;

    // Costruttore che imposta i topic MQTT specifici della cella robotica
    public RobotCell(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
        this.statusTopic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
        this.cmdTopic = String.format("mf/%s/%s/%s/cmd", cellId, deviceType, deviceId);
        this.ackTopic = String.format("mf/%s/%s/%s/ack", cellId, deviceType, deviceId);
    }

    // Ciclo principale che pubblica telemetria e reagisce ai comandi in base allo stato corrente
    @Override
    public void start() throws InterruptedException {
        logger.info("RobotCell {} started.", deviceId);
        subscribeToCommands();

        while (running) {
            switch (currentState) {
                case IDLE:
                    handleIdleState();
                    break;
                case PROCESSING:
                    handleProcessingState();
                    break;
                case ALARM:
                    handleAlarmState();
                    break;
            }
            Thread.sleep(100); // Piccolo ritardo per evitare un loop troppo aggressivo
        }
    }

    // Gestisce lo stato di inattività simulando un tempo di attesa prima della produzione
    private void handleIdleState() throws InterruptedException {
        publishStatus(0);
        Thread.sleep(IDLE_DURATION_MS);
        if (running) {
            this.currentState = RobotCellStatusEnum.PROCESSING;
            logger.info("Robot {} state changed to PROCESSING", deviceId);
        }
    }

    // Simula l'elaborazione di un lotto decidendo durata e possibili allarmi casuali
    private void handleProcessingState() throws InterruptedException {
        double processingTime = random.nextInt(MAX_PROCESSING_DURATION_MS);
        publishStatus(processingTime / 1000.0);
        Thread.sleep((long) processingTime);

        if (!running) return; // Esce se lo shutdown è stato richiesto durante la pausa

        if (random.nextDouble() < ALARM_PROBABILITY) {
            this.currentState = RobotCellStatusEnum.ALARM;
            logger.warn("Robot {} state changed to ALARM", deviceId);
        } else {
            this.currentState = RobotCellStatusEnum.IDLE;
            logger.info("Robot {} finished processing, state changed to IDLE", deviceId);
        }
    }

    // Mantiene il robot in allarme finché non arriva un comando esterno di reset
    private void handleAlarmState() throws InterruptedException {
        publishStatus(0);
        logger.error("Robot {} is in ALARM state. Waiting for external RESET command...", deviceId);
        while (running && this.currentState == RobotCellStatusEnum.ALARM) {
            Thread.sleep(1000); // Attende finché un comando esterno cambia lo stato
        }
    }

    // Sottoscrive il topic dei comandi per ricevere i messaggi di controllo via MQTT
    private void subscribeToCommands() {
        try {
            mqttClientManager.getClient().subscribe(cmdTopic, 1, this::handleCommandMessage);
            logger.info("Subscribed to command topic: {}", cmdTopic);
        } catch (MqttException e) {
            logger.error("Failed to subscribe to command topic {}", cmdTopic, e);
        }
    }

    // Decodifica il comando ricevuto dal topic MQTT e lo inoltra alla logica di gestione
    private void handleCommandMessage(String topic, MqttMessage message) {
        try {
            Command cmd = objectMapper.readValue(message.getPayload(), Command.class);
            logger.info("Received command: {} on topic {}", cmd.getType(), topic);
            handleCommand(cmd);
        } catch (Exception e) {
            logger.error("Error processing command message", e);
        }
    }

    // Applica i comandi supportati aggiornando lo stato del robot e inviando un ACK
    private void handleCommand(Command cmd) {
        String status = "OK";
        String message = "Command executed successfully";

        switch (cmd.getType()) {
            case "START":
                if (currentState == RobotCellStatusEnum.IDLE) {
                    this.currentState = RobotCellStatusEnum.PROCESSING;
                } else {
                    status = "ERROR";
                    message = "Cannot start, not in IDLE state.";
                }
                break;
            case "STOP":
                this.currentState = RobotCellStatusEnum.IDLE;
                break;
            case "RESET":
                if (currentState == RobotCellStatusEnum.ALARM) {
                    this.currentState = RobotCellStatusEnum.IDLE;
                    logger.info("Robot {} reset from ALARM state.", deviceId);
                } else {
                    status = "ERROR";
                    message = "Cannot reset, not in ALARM state.";
                }
                break;
            default:
                status = "ERROR";
                message = "Unknown command type: " + cmd.getType();
                logger.warn(message);
        }
        publishAck(cmd.getType(), status, message, cmd.getMsgId());
    }

    // Pubblica sul topic di stato i dati della cella robotica simulata
    private void publishStatus(double processingTime) {
        RobotCellStatus status = new RobotCellStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.currentState,
                processingTime
        );
        mqttClientManager.publish(statusTopic, status);
    }

    // Invia un messaggio di riscontro per informare il chiamante sull'esito del comando
    private void publishAck(String cmdType, String status, String message, String msgId) {
        Ack ack = new Ack(cmdType, status, message, System.currentTimeMillis(), msgId);
        mqttClientManager.publish(ackTopic, ack);
    }
}
