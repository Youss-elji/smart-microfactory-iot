package it.unimore.iot.microfactory.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.Ack;
import it.unimore.iot.microfactory.model.Command;
import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.Locale;
import java.util.Random;

public class ConveyorBelt extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(ConveyorBelt.class);

    // Durate e parametri simulazione
    private static final int ACTIVE_DURATION_MS = 15_000;
    private static final int INACTIVE_DURATION_MS = 5_000;
    private static final double BASE_SPEED = 10.0;      // items/min
    private static final double SPEED_VARIATION = 2.0;  // ±2

    private final Random random = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean active = false;

    public ConveyorBelt(String cellId, String deviceType, String deviceId) {
        super(cellId, deviceType, deviceId);
    }

    @Override
    public void start() throws InterruptedException {
        logger.info("ConveyorBelt {} started.", deviceId);

        // Subscribe ai comandi
        String cmdTopic = String.format("mf/%s/%s/%s/cmd", cellId, deviceType, deviceId);
        try {
            mqttClientManager.getClient().subscribe(cmdTopic, 1, (t, msg) -> {
                try {
                    Command cmd = objectMapper.readValue(msg.getPayload(), Command.class);
                    handleCommand(cmd);
                } catch (Exception e) {
                    logger.error("Error handling command on {}: {}", t, e.getMessage(), e);
                }
            });
        } catch (MqttException e) {
            logger.error("Failed to subscribe to command topic {} for {}", cmdTopic, deviceId, e);
        }

        // Ciclo di simulazione ON/OFF
        while (running && !Thread.currentThread().isInterrupted()) {
            if (active) {
                publishStatus();
                Thread.sleep(ACTIVE_DURATION_MS + random.nextInt(3_000));
                active = false;
            } else {
                publishStatus();
                Thread.sleep(INACTIVE_DURATION_MS + random.nextInt(1_000));
                active = true;
            }
        }
    }

    private void handleCommand(Command cmd) {
        String type = (cmd.getType() == null ? "" : cmd.getType().toUpperCase(Locale.ROOT));
        boolean ok = true;
        String message;

        switch (type) {
            case "START":
                active = true;
                message = "conveyor started";
                break;
            case "STOP":
                active = false;
                message = "conveyor stopped";
                break;
            case "RESET":
                // nessuna logica speciale, conferma
                message = "conveyor reset OK";
                break;
            default:
                ok = false;
                message = "unknown command: " + type;
        }

        Ack ack = new Ack(type, ok ? "OK" : "ERROR", message, System.currentTimeMillis());
        String ackTopic = String.format("mf/%s/%s/%s/ack", cellId, deviceType, deviceId);
        mqttClientManager.publish(ackTopic, ack);   // niente try/catch: publish non dichiara più throws
    }

    private void publishStatus() {
        double currentSpeed = 0.0;
        if (active) {
            currentSpeed = BASE_SPEED + (random.nextDouble() * SPEED_VARIATION * 2) - SPEED_VARIATION;
            currentSpeed = Math.max(0.0, currentSpeed);
        }

        ConveyorBeltStatus status = new ConveyorBeltStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.active,
                currentSpeed
        );

        String topic = String.format("mf/%s/%s/%s/status", cellId, deviceType, deviceId);
        mqttClientManager.publish(topic, status);   // anche qui senza try/catch
    }
}
