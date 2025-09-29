# Smart Microfactory IoT Project

This project simulates a smart microfactory using **Java 17** and **MQTT** for communication between different smart devices (Robot, Conveyor, Quality Sensor).  
The **Manager** component also exposes a **CoAP API** (Californium) for external monitoring and querying, aligned with typical **lab requirements** (CoRE Link Format, SenML+JSON, Observe, automatic client).

---

## Overview

Main components:
- **RobotCell** – simulated robot arm that processes items.  
- **ConveyorBelt** – simulated conveyor belt that moves items.  
- **QualitySensor** – simulated quality inspection sensor.  
- **DataCollectorManager** – collects device telemetry/events via MQTT and updates the digital twin (state repo).  
- **CoAP API Server** – exposes CoAP resources to query/observe device states in real time.

---

## Build & Run

Prerequisites: **Java 17** and **Maven**.

1) Build the fat JAR:
```bash
mvn clean package
```

2) Ensure an MQTT broker is running (e.g., Mosquitto on `tcp://localhost:1883`). Then run:
```bash
java -jar target/smart-microfactory-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

Alternative (via Maven exec):
```bash
mvn exec:java
```

> Tip: If you need a clean build without CoAP tests, use the profile:
> ```bash
> mvn -P no-coap-tests clean package
> ```

---

## MQTT Topic Schema

Structured topic layout:

- **Telemetry**  
  `mf/<cell>/<type>/<id>/status`  
  e.g., `mf/cell-01/robot/robot-001/status`

- **Info (retained)**  
  `mf/<cell>/<type>/<id>/info`  
  Published at connect; device metadata.

- **LWT (retained)**  
  `mf/<cell>/<type>/<id>/lwt`  
  Payload: `"offline"` (via MQTT Last Will).

- **Commands**  
  `mf/<cell>/<type>/<id>/cmd`  
  e.g., `START`, `STOP`, `RESET`.

- **Ack**  
  `mf/<cell>/<type>/<id>/ack`  
  Device acknowledgments to commands.

### Quick MQTT Test (optional)
```bash
# Start broker
mosquitto -v

# Subscribe to everything
mosquitto_sub -v -t "mf/#"

# Send a RESET command
mosquitto_pub -t "mf/cell-01/robot/robot-001/cmd" -m '{"type":"RESET","ts":1699999999}'
```

---

## CoAP API – Lab Compliance

The manager exposes a CoAP API on `udp://localhost:5683`.  
It implements **CoRE Link Format** discovery, **content negotiation** including **SenML+JSON**, and **OBSERVE** on dynamic resources.

### Endpoints & Features

- **Resource Discovery** (CoRE Link Format)  
  `GET /.well-known/core`  
  Each resource declares:
  - `rt` (Resource Type), e.g. `it.unimore.device.sensor.temperature`, `it.unimore.device.sensor.capsule`, `it.unimore.device.actuator.task`
  - `if` (Interface Description), typically `core.s` for sensors, `core.a` for actuators
  - `ct` (supported Content-Formats), including `application/senml+json` and `text/plain`

- **Device listing**  
  `GET /factory/{cellId}/devices`

- **Device state (single resource)**  
  `GET /factory/{cellId}/{deviceType}/{deviceId}/state`  
  Supports **content negotiation** via `Accept`:
  - `application/senml+json` (ID 110)
  - `application/json`
  - `text/plain` (fallback)

- **Observe (RFC 7641)**  
  Same state endpoint with the `Observe` option to receive notifications on changes.

---

## Testing CoAP with `coap-client` (WSL/Linux, optional)

If `libcoap3-bin` is installed, use `coap-client` (or `coap-client-notls` depending on distro):

1) **Discover resources**  
```bash
coap-client -m get coap://localhost:5683/.well-known/core
# or: /usr/bin/coap-client-notls -m get ...
```

2) **Get device state in SenML+JSON**  
```bash
# -A 110 sets Accept to application/senml+json
coap-client -m get -A 110 coap://localhost:5683/factory/cell-01/robot/robot-001/state
```

3) **Observe for 60 seconds**  
```bash
coap-client -m get -s 60 coap://localhost:5683/factory/cell-01/robot/robot-001/state
```

---

## Automatic Java Client (Discovery → Validation → Action)

The project includes an **automatic CoAP client** that:
1) Fetches and parses `/.well-known/core`  
2) Validates `rt`/`if`/`ct`  
3) Reads sensor state (SenML)  
4) Interacts with an actuator (POST/PUT)

Run it from another terminal (with the app running):
```bash
mvn -q exec:java -Dexec.mainClass="it.unimore.iot.microfactory.coap.client.CoapAutomaticClient"
```

---

## Notes & Troubleshooting

- On Windows/WSL, if UDP tooling (e.g., `netstat | grep`) is unreliable, prefer the **Java-based probes** included in the project to verify `/.well-known/core`.  
- If port `5683` is busy, stop other CoAP servers or adjust the configured port for local tests.

---

## License

MIT (unless otherwise specified in submodules).
