package it.unimore.iot.microfactory.communication.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimore.iot.microfactory.model.Command;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Publishes commands received via the CoAP API onto the MQTT broker so that
 * simulated devices can consume them.
 */
public class CommandPublisher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CommandPublisher.class);
    private static final String CLIENT_ID_PREFIX = "command-publisher-";
    private static final String GLOBAL_COMMAND_TOPIC = "mf/broadcast/cmd";

    private final ObjectMapper mapper = new ObjectMapper();
    private final IMqttClient client;
    private final String brokerUrl;
    private boolean connected;

    public CommandPublisher() throws MqttException {
        this.brokerUrl = Optional.ofNullable(System.getenv("MQTT_BROKER_URL"))
                .orElse("tcp://localhost:1883");
        String clientId = CLIENT_ID_PREFIX + UUID.randomUUID();
        this.client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
    }

    /**
     * Connects the internal MQTT client if not already connected.
     */
    public synchronized void start() throws MqttException {
        if (connected) {
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        Optional.ofNullable(System.getenv("MQTT_USERNAME")).ifPresent(options::setUserName);
        Optional.ofNullable(System.getenv("MQTT_PASSWORD"))
                .map(String::toCharArray)
                .ifPresent(options::setPassword);

        client.connect(options);
        connected = true;
        logger.info("CommandPublisher connected to MQTT broker {}", brokerUrl);
    }

    /**
     * Publishes a command addressed to a specific device.
     */
    public synchronized void publishDeviceCommand(String cellId, String deviceType, String deviceId, Command command)
            throws MqttException {
        ensureConnected();
        String topic = String.format("mf/%s/%s/%s/cmd", cellId, deviceType, deviceId);
        publish(topic, command);
    }

    /**
     * Publishes a broadcast command intended for every device in the factory.
     */
    public synchronized void publishGlobalCommand(Command command) throws MqttException {
        ensureConnected();
        publish(GLOBAL_COMMAND_TOPIC, command);
    }

    private void publish(String topic, Command command) throws MqttException {
        try {
            byte[] payload = mapper.writeValueAsBytes(command);
            client.publish(topic, payload, 1, false);
            logger.info("Published command {} to {}", command.getType(), topic);
        } catch (Exception e) {
            if (e instanceof MqttException mqttException) {
                throw mqttException;
            }
            throw new MqttException(e);
        }
    }

    private void ensureConnected() throws MqttException {
        if (!connected || !client.isConnected()) {
            start();
        }
    }

    @Override
    public synchronized void close() throws MqttException {
        if (client.isConnected()) {
            client.disconnect();
            logger.info("CommandPublisher disconnected from MQTT broker {}", brokerUrl);
        }
        connected = false;
    }
}
