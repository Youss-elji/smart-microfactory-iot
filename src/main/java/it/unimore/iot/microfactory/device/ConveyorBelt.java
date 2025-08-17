package it.unimore.iot.microfactory.device;

import it.unimore.iot.microfactory.model.ConveyorBeltStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ConveyorBelt extends SimulatedDevice {

    private static final Logger logger = LoggerFactory.getLogger(ConveyorBelt.class);

    private static final double BASE_SPEED = 10.0; // items per minute
    private static final double SPEED_VARIATION = 2.0;

    private final Random random = new Random();

    }

    @Override
    public void start() throws InterruptedException {
        logger.info("ConveyorBelt {} started.", deviceId);

                publishStatus();
                this.active = true;
            }
        }
    }

    private void publishStatus() {
        double currentSpeed = 0;
        if (this.active) {
            currentSpeed = BASE_SPEED + (random.nextDouble() * SPEED_VARIATION * 2) - SPEED_VARIATION;
            currentSpeed = Math.max(0, currentSpeed); // Ensure speed is not negative
        }

        ConveyorBeltStatus status = new ConveyorBeltStatus(
                this.deviceId,
                System.currentTimeMillis(),
                this.active,
                currentSpeed
        );
        mqttClientManager.publish(topic, status);
    }
}
