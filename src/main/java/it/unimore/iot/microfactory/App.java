package it.unimore.iot.microfactory;

import it.unimore.iot.microfactory.adapters.coap.CoapApiServer;
import it.unimore.iot.microfactory.communication.mqtt.CommandPublisher;
import it.unimore.iot.microfactory.device.simulator.ConveyorBelt;
import it.unimore.iot.microfactory.device.simulator.QualitySensor;
import it.unimore.iot.microfactory.device.simulator.RobotCell;
import it.unimore.iot.microfactory.domain.StateRepository;
import it.unimore.iot.microfactory.manager.DataCollectorManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Smart Microfactory Simulation...");

        CommandPublisher commandPublisher = null;
        try {
            // Get the shared state repository instance
            StateRepository stateRepository = StateRepository.getInstance();

            // Bridge CoAP commands towards MQTT
            commandPublisher = new CommandPublisher();
            commandPublisher.start();
            stateRepository.registerCommandPublisher(commandPublisher);

            // Create and start the Data Collector Manager
            DataCollectorManager dataCollectorManager = new DataCollectorManager();
            dataCollectorManager.start();

            // Create and start the CoAP API Server
            CoapApiServer coapApiServer = new CoapApiServer(stateRepository);
            coapApiServer.start();

            // Create device instances
            String cell = "cell-01";
            RobotCell robot = new RobotCell(cell, "robot", "robot-001");
            ConveyorBelt conveyor = new ConveyorBelt(cell, "conveyor", "conveyor-001");
            QualitySensor sensor = new QualitySensor(cell, "quality", "sensor-qs-001");

            List<Thread> deviceThreads = List.of(
                    new Thread(robot, "robot-thread"),
                    new Thread(conveyor, "conveyor-thread"),
                    new Thread(sensor, "sensor-thread")
            );

            // Start all device threads
            deviceThreads.forEach(Thread::start);

            logger.info("All components have been started.");

            // Add a shutdown hook to gracefully stop all components
            CommandPublisher finalCommandPublisher = commandPublisher;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping all components...");

                // Stop devices
                robot.shutdown();
                conveyor.shutdown();
                sensor.shutdown();

                // Interrupt threads to unblock any waiting operations
                deviceThreads.forEach(Thread::interrupt);

                // Wait for all threads to terminate
                for (Thread t : deviceThreads) {
                    try {
                        t.join(2000); // Wait max 2 seconds for each thread
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while waiting for thread {} to finish.", t.getName(), e);
                    }
                }

                // Stop the CoAP server
                coapApiServer.stop();

                // Stop the MQTT manager
                try {
                    dataCollectorManager.stop();
                } catch (MqttException e) {
                    logger.error("Error stopping data collector manager", e);
                }

                if (finalCommandPublisher != null) {
                    try {
                        finalCommandPublisher.close();
                    } catch (MqttException e) {
                        logger.error("Error closing command publisher", e);
                    }
                }
                logger.info("Simulation shut down gracefully.");
            }));

        } catch (MqttException e) {
            logger.error("An error occurred during simulation setup.", e);
            if (commandPublisher != null) {
                try {
                    commandPublisher.close();
                } catch (MqttException ex) {
                    logger.error("Error closing command publisher after failure", ex);
                }
            }
        }
    }
}
