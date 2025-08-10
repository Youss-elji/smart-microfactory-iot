package it.unimore.iot.microfactory;

import it.unimore.iot.microfactory.device.ConveyorBelt;
import it.unimore.iot.microfactory.device.QualitySensor;
import it.unimore.iot.microfactory.device.RobotCell;
import it.unimore.iot.microfactory.manager.DataCollectorManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Smart Microfactory Simulation...");

        try {
            // Create and start the Data Collector Manager
            DataCollectorManager dataCollectorManager = new DataCollectorManager();
            dataCollectorManager.start();

            // Create device instances
            RobotCell robot = new RobotCell("robot-001");
            ConveyorBelt conveyor = new ConveyorBelt("conveyor-001");
            QualitySensor sensor = new QualitySensor("sensor-qs-001");

            // Start each device in its own thread
            new Thread(robot, "robot-thread").start();
            new Thread(conveyor, "conveyor-thread").start();
            new Thread(sensor, "sensor-thread").start();

            logger.info("All devices have been started.");

            // Add a shutdown hook to gracefully stop the manager
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    dataCollectorManager.stop();
                } catch (MqttException e) {
                    logger.error("Error stopping data collector manager", e);
                }
                logger.info("Simulation shut down gracefully.");
            }));

        } catch (MqttException e) {
            logger.error("An error occurred during simulation setup.", e);
        }
    }
}
