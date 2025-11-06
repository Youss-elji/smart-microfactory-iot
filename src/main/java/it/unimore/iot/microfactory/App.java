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

    // Punto di ingresso dell'applicazione che avvia tutti i componenti della microfabbrica simulata
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    // Avvia il simulatore inizializzando servizi di comunicazione, dispositivi e chiusura controllata
    public static void main(String[] args) {
        logger.info("Starting Smart Microfactory Simulation...");

        CommandPublisher commandPublisher = null;
        try {
            // Recupera l'istanza condivisa del repository dello stato della microfabbrica
            StateRepository stateRepository = StateRepository.getInstance();

            // Avvia il bridge che inoltra i comandi CoAP verso l'infrastruttura MQTT
            commandPublisher = new CommandPublisher();
            commandPublisher.start();
            stateRepository.registerCommandPublisher(commandPublisher);

            // Avvia il gestore che raccoglie i dati dai dispositivi MQTT
            DataCollectorManager dataCollectorManager = new DataCollectorManager();
            dataCollectorManager.start();

            // Avvia il server CoAP che espone l'API per il controllo della microfabbrica
            CoapApiServer coapApiServer = new CoapApiServer(stateRepository);
            coapApiServer.start();

            // Istanzia i dispositivi simulati appartenenti alla stessa cella produttiva
            String cell = "cell-01";
            RobotCell robot = new RobotCell(cell, "robot", "robot-001");
            ConveyorBelt conveyor = new ConveyorBelt(cell, "conveyor", "conveyor-001");
            QualitySensor sensor = new QualitySensor(cell, "quality", "sensor-qs-001");

            List<Thread> deviceThreads = List.of(
                    new Thread(robot, "robot-thread"),
                    new Thread(conveyor, "conveyor-thread"),
                    new Thread(sensor, "sensor-thread")
            );

            // Avvia i thread associati ai dispositivi simulati
            deviceThreads.forEach(Thread::start);

            logger.info("All components have been started.");

            // Registra una shutdown hook che arresta i componenti in modo ordinato
            CommandPublisher finalCommandPublisher = commandPublisher;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping all components...");

                // Arresta i dispositivi simulati
                robot.shutdown();
                conveyor.shutdown();
                sensor.shutdown();

                // Interrompe i thread per sbloccare eventuali attese
                deviceThreads.forEach(Thread::interrupt);

                // Attende la terminazione dei thread con un timeout di sicurezza
                for (Thread t : deviceThreads) {
                    try {
                        t.join(2000); // Attende al massimo 2 secondi per ciascun thread
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while waiting for thread {} to finish.", t.getName(), e);
                    }
                }

                // Arresta il server CoAP
                coapApiServer.stop();

                // Arresta il gestore dei dati MQTT
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
