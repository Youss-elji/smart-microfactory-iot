# Smart Microfactory IoT Project

This project simulates a smart microfactory using Java and MQTT for communication between different smart devices. The manager component also exposes a CoAP API for external monitoring and querying.

## How to Build and Run

You need Java 17 and Maven installed.

1.  **Build the project and create an executable JAR**:
    ```bash
    mvn clean package
    ```

2.  **Run the simulation**:
    First, ensure an MQTT broker (like Mosquitto) is running on `tcp://localhost:1883`. Then, run the application using the generated JAR:
    ```bash
    java -jar target/smart-microfactory-0.0.1-SNAPSHOT-jar-with-dependencies.jar
    ```

## Topic Schema & Test

The communication between devices and the manager follows a structured MQTT topic schema.

-   **Telemetry**: `mf/<cell>/<type>/<id>/status` (e.g., `mf/cell-01/robot/robot-001/status`)
-   **Info (retained)**: `mf/<cell>/<type>/<id>/info` (Published on connect, contains device metadata)
-   **LWT (retained)**: `mf/<cell>/<type>/<id>/lwt` (Payload: `"offline"`)
-   **Commands**: `mf/<cell>/<type>/<id>/cmd` (Used to send commands like `START`, `STOP`, `RESET`)
-   **Ack**: `mf/<cell>/<type>/<id>/ack` (Used by devices to acknowledge commands)

## CoAP API (Step 3)

The manager also exposes a CoAP API on `udp://localhost:5683` for querying the state of the factory.

### Endpoints

-   **Discover available resources**:
    `GET /.well-known/core`

-   **List all known devices in a cell**:
    `GET /factory/{cellId}/devices`

-   **Get the last known state of a specific device**:
    `GET /factory/{cellId}/{deviceType}/{deviceId}/state`

-   **Observe a device's state for real-time updates**:
    `GET /factory/{cellId}/{deviceType}/{deviceId}/state` (with `Observe` option)

### Testing with `coap-client`

You can use `coap-client` (from `libcoap`) to interact with the API.

1.  **Discover resources**:
    ```bash
    coap-client -m get coap://localhost:5683/.well-known/core
    ```

2.  **List devices in `cell-01`**:
    ```bash
    coap-client -m get coap://localhost:5683/factory/cell-01/devices
    ```
    *Example Response:*
    ```json
    {
      "cell": "cell-01",
      "devices": [
        {"type":"robot","id":"robot-001"},
        {"type":"conveyor","id":"conveyor-001"},
        {"type":"quality","id":"sensor-qs-001"}
      ]
    }
    ```

3.  **Get state of `robot-001`**:
    ```bash
    coap-client -m get coap://localhost:5683/factory/cell-01/robot/robot-001/state
    ```

4.  **Observe state of `robot-001` for 1 minute (push notifications)**:
    ```bash
    coap-client -m get -s 60 coap://localhost:5683/factory/cell-01/robot/robot-001/state
    ```
    *Note: You will receive notifications automatically whenever the robot's state changes.*