package it.unimore.iot.microfactory;

import it.unimore.iot.microfactory.device.ConveyorBelt;
import it.unimore.iot.microfactory.device.QualitySensor;
import it.unimore.iot.microfactory.device.RobotCell;
import it.unimore.iot.microfactory.manager.DataCollectorManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Starting Smart Microfactory Simulation...");

        try {
            // Create and start the Data Collector Manager
            DataCollectorManager dataCollectorManager = new DataCollectorManager();
            dataCollectorManager.start();

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

            logger.info("All devices have been started.");

            // Add a shutdown hook to gracefully stop all components
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

                // Stop the manager
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
