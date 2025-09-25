# Smart Microfactory IoT Project

This project simulates a smart microfactory using Java and MQTT for communication between different smart devices.

## Overview

The simulation consists of the following components:
- **RobotCell**: A simulated robot arm that processes items.
- **ConveyorBelt**: A simulated conveyor belt that moves items.
- **QualitySensor**: A simulated sensor that inspects items for quality.
- **DataCollectorManager**: A central component that collects data from all devices, monitors their status, and can send commands.

## Topic Schema & Test

The communication between devices and the manager follows a structured MQTT topic schema.

-   **Telemetry**: `mf/<cell>/<type>/<id>/status` (e.g., `mf/cell-01/robot/robot-001/status`)
-   **Info (retained)**: `mf/<cell>/<type>/<id>/info` (Published on connect, contains device metadata)
-   **LWT (retained)**: `mf/<cell>/<type>/<id>/lwt` (Payload: `"offline"`)
-   **Commands**: `mf/<cell>/<type>/<id>/cmd` (Used to send commands like `START`, `STOP`, `RESET`)
-   **Ack**: `mf/<cell>/<type>/<id>/ack` (Used by devices to acknowledge commands)

### Quick Test with Mosquitto

You can test the system using the `mosquitto` command-line tools.

1.  **Start the MQTT Broker**:
    ```bash
    mosquitto -v
    ```

2.  **Subscribe to all topics to monitor the traffic**:
    ```bash
    mosquitto_sub -v -t "mf/#"
    ```

3.  **Run the application**:
    You can run the application from your IDE or using Maven. To set the broker URL, use an environment variable.

    In PowerShell:
    ```powershell
    $env:MQTT_BROKER_URL="tcp://localhost:1883"; mvn -q exec:java
    ```

    In Bash:
    ```bash
    export MQTT_BROKER_URL="tcp://localhost:1883"
    mvn -q exec:java
    ```

4.  **Send a command**:
    When a robot enters the `ALARM` state, you can manually send a `RESET` command (though the manager should do this automatically).

    ```bash
    # Note: The timestamp part might need adjustment depending on your shell.
    # For PowerShell:
    mosquitto_pub -t "mf/cell-01/robot/robot-001/cmd" -m "{\"type\":\"RESET\",\"ts\":$(Get-Date -UFormat %s)000 }"

    # For Bash:
    mosquitto_pub -t "mf/cell-01/robot/robot-001/cmd" -m "{\"type\":\"RESET\",\"ts\":$(date +%s)000}"
    ```

## How to Build and Run

You need Java and Maven installed.

1.  **Clean and build the project**:
    ```bash
    mvn clean install
    ```

2.  **Run the simulation**:
    ```bash
    mvn exec:java
    ```