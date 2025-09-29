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

## CoAP API – Lab Compliance

The manager exposes a CoAP API on `udp://localhost:5683` for querying the state of the factory, compliant with typical lab requirements.

### Endpoints & Features

-   **Resource Discovery**: `GET /.well-known/core`
    -   Resources are described using CoRE Link Format, including attributes for `rt` (Resource Type), `if` (Interface Description), and `ct` (Content-Type).
-   **Content Negotiation**: Device state resources support multiple content formats via the `Accept` option:
    -   `application/senml+json` (Content-Format ID: 110)
    -   `application/json` (Default)
    -   `text/plain`
-   **State Observation**: Sensor and actuator resources are "observable" (RFC 7641), allowing clients to subscribe to real-time state changes.

### Testing with `coap-client` (Optional, for WSL/Linux)

If you have `libcoap3-bin` installed (e.g., on WSL/Ubuntu), you can use `coap-client-notls`:

1.  **Discover resources and their attributes**:
    ```bash
    /usr/bin/coap-client-notls -m get coap://localhost:5683/.well-known/core
    ```

2.  **Get device state in SenML+JSON format**:
    ```bash
    # Use -A to specify the Accept option for SenML+JSON (ID 110)
    /usr/bin/coap-client-notls -m get -A 110 coap://localhost:5683/factory/cell-01/robot/robot-001/state
    ```

3.  **Observe state of `robot-001`**:
    ```bash
    /usr/bin/coap-client-notls -m get -s 60 coap://localhost:5683/factory/cell-01/robot/robot-001/state
    ```

### Testing with the Automatic Java Client

The project includes an automatic client that performs discovery, validation, and interaction.

1.  **Ensure the main application is running.**
2.  **Run the automatic client from a new terminal**:
    ```bash
    mvn -q exec:java -Dexec.mainClass="it.unimore.iot.microfactory.coap.client.CoapAutomaticClient"
    ```
    The client will log its actions, from discovering resources to finding the required sensor/actuator and triggering a command.