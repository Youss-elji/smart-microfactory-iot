# smart-microfactory-iot
Simulazione in Java di una microfactory intelligente con RobotCell, ConveyorBelt e QualitySensor, usando MQTT, CoAP e REST, con supporto Digital Twin.

## Run
1) Avvia un broker MQTT (Mosquitto): `mosquitto -v`
2) Esegui:
   - Windows PowerShell:
     `$env:MQTT_BROKER_URL="tcp://localhost:1883"; mvn -q exec:java`
   - Linux/macOS:
     `MQTT_BROKER_URL=tcp://localhost:1883 mvn -q exec:java`

## Topic schema
- Telemetry: `mf/<cell>/<type>/<id>/status`
- Info (retained): `mf/<cell>/<type>/<id>/info`
- LWT: `mf/<cell>/<type>/<id>/lwt` (payload: `offline`, retained)
- Cmd/Ack: `mf/<cell>/<type>/<id>/{cmd|ack}`
