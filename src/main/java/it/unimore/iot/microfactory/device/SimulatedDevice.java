package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.communication.MqttClientManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SimulatedDevice implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedDevice.class);

    protected final String cellId;
    protected final String deviceType;
    protected final String deviceId;
    protected final MqttClientManager mqttClientManager;
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected volatile boolean running = true;

    protected SimulatedDevice(String cellId, String deviceType, String deviceId) {
        this.cellId = cellId;
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        try {
            this.mqttClientManager = new MqttClientManager(cellId, deviceType, deviceId);
        } catch (MqttException e) {
            logger.error("Failed to create MQTT client for device {}", deviceId, e);
            throw new RuntimeException("MQTT client creation failed", e);
        }
    }

    @Override
    public void run() {
        try {
            mqttClientManager.connect();
            start(); // This will call the specific implementation of the subclass
        } catch (MqttException e) {
            logger.error("Error during device execution for {}", deviceId, e);
        } catch (InterruptedException e) {
            if (running) { // Only log as a warning if shutdown was not called
                logger.warn("Device {} was interrupted unexpectedly.", deviceId);
            }
            Thread.currentThread().interrupt();
        } finally {
            try {
                mqttClientManager.disconnect();
            } catch (MqttException e) {
                logger.error("Error disconnecting MQTT client for device {}", deviceId, e);
            }
            logger.info("Device {} shutdown complete.", deviceId);
        }
    }

    public void shutdown() {
        this.running = false;
        logger.info("Shutdown requested for device {}.", deviceId);
    }

    /**
     * The main simulation logic for the device goes here.
     * This method is called after the MQTT client is connected.
     * @throws InterruptedException if the thread is interrupted.
     */
    public abstract void start() throws InterruptedException;
}
