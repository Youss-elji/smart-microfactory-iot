# Smart Microfactory IoT Project

This project simulates a smart microfactory using Java and MQTT for communication between different smart devices.  
The manager component also exposes a CoAP API for external monitoring and querying.

## Overview

The simulation consists of the following components:
- **RobotCell**: A simulated robot arm that processes items.
- **ConveyorBelt**: A simulated conveyor belt that moves items.
- **QualitySensor**: A simulated sensor that inspects items for quality.
- **DataCollectorManager**: A central component that collects data from all devices, monitors their status, and can send commands.
- **CoAP API Server**: Provides CoAP endpoints to query and observe device states in real time.

## MQTT Topic Schema

The communication between devices and the manager follows a structured MQTT topic schema:

- **Telemetry**:  
  `mf/<cell>/<type>/<id>/status`  
  Example: `mf/cell-01/robot/robot-001/status`

- **Info (retained)**:  
  `mf/<cell>/<type>/<id>/info`  
  Published on connect, contains device metadata.

- **LWT (retained)**:  
  `mf/<cell>/<type>/<id>/lwt`  
  Payload: `"offline"`

- **Commands**:  
  `mf/<cell>/<type>/<id>/cmd`  
  Used to send commands like `START`, `STOP`, `RESET`.

- **Ack**:  
  `mf/<cell>/<type>/<id>/ack`  
  Used by devices to acknowledge commands.

## CoAP API

The manager exposes a CoAP API on `udp://localhost:5683` for querying the state of the factory.

### Endpoints

- **Discover available resources**  
  `GET /.well-known/core`

- **List all known devices in a cell**  
  `GET /factory/{cellId}/devices`

- **Get the last known state of a specific device**  
  `GET /factory/{cellId}/{deviceType}/{deviceId}/state`

- **Observe a device's state for real-time updates**  
  `GET /factory/{cellId}/{deviceType}/{deviceId}/state` (with `Observe` option)

### Example with `coap-client`

1. Discover resources:
   ```bash
   coap-client -m get coap://localhost:5683/.well-known/core
   ```

2. List devices in `cell-01`:
   ```bash
   coap-client -m get coap://localhost:5683/factory/cell-01/devices
   ```

3. Get state of `robot-001`:
   ```bash
   coap-client -m get coap://localhost:5683/factory/cell-01/robot/robot-001/state
   ```

4. Observe state of `robot-001` for 1 minute:
   ```bash
   coap-client -m get -s 60 coap://localhost:5683/factory/cell-01/robot/robot-001/state
   ```

## Build and Run

You need **Java 17** and **Maven** installed.

1. **Build the project and create an executable JAR**:
   ```bash
   mvn clean package
   ```

2. **Run the simulation** (make sure an MQTT broker like Mosquitto is running on `tcp://localhost:1883`):
   ```bash
   java -jar target/smart-microfactory-0.0.1-SNAPSHOT-jar-with-dependencies.jar
   ```

3. **Run the simulation via Maven**:
   ```bash
   mvn exec:java
   ```

## Quick Test with Mosquitto

1. Start the MQTT Broker:
   ```bash
   mosquitto -v
   ```

2. Subscribe to all topics:
   ```bash
   mosquitto_sub -v -t "mf/#"
   ```

3. Publish a test command (RESET):
   ```bash
   mosquitto_pub -t "mf/cell-01/robot/robot-001/cmd" -m "{\"type\":\"RESET\",\"ts\":$(date +%s)000}"
   ```

---
