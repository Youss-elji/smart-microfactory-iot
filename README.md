# 🏭 Smart Microfactory IoT

Simulazione completa di una micro-fabbrica con dispositivi robotici, nastri trasportatori e sensori di qualità. Il progetto espone un'API REST-like tramite CoAP, pubblica telemetria su MQTT e mantiene un digital twin in memoria per orchestrare i flussi di dati.

## ✨ Caratteristiche principali
- **API CoAP dinamica** (`/factory/{cell}/{type}/{id}`) con risorse osservabili e content negotiation (JSON, text/plain, SenML).
- **Bridging CoAP → MQTT**: i comandi ricevuti via CoAP vengono serializzati nel modello `Command` e inoltrati al topic MQTT del dispositivo tramite `CommandPublisher`.
- **Digital twin centralizzato** (`StateRepository`) aggiornato dal `DataCollectorManager` che si sottoscrive ai topic di stato.
- **Simulatori MQTT** per robot, nastri e sensori che pubblicano stato (`mf/<cell>/<type>/<id>/status`) e gestiscono comandi (`mf/.../cmd`) rispondendo con `Ack`.
- **Auto reset opzionale** dei robot in allarme (`AUTO_RESET_ON_ALARM=true`).

## 🧱 Architettura logica
```
┌────────────────┐       CoAP         ┌────────────────────┐       MQTT       ┌─────────────────┐
│  Client CoAP   │ ─────────────────▶ │  CoapApiServer     │ ───────────────▶ │ CommandPublisher │
│  (tester, app) │   observe + cmd    │    + StateRepository│   Command JSON  │  (MQTT bridge)   │
└────────────────┘                    │    (digital twin)  │◀─────────────── │                 │
          ▲                           └─────────┬──────────┘    Status JSON   └──────┬──────────┘
          │                                       │                            │
          │ observe                               │ updates                    │
          │                                       ▼                            ▼
          │                              ┌────────────────────┐       ┌────────────────────┐
          │                              │ DataCollectorManager│◀──── │  Dispositivi MQTT  │
          │                              │ (subscribe status) │       │  simulati (robot,  │
          │                              └────────────────────┘       │  conveyor, sensor) │
          │                                                            └────────────────────┘
```

## ✅ Prerequisiti
- Java 17+
- Maven 3.6+
- Un broker MQTT (Mosquitto consigliato).
- (Opzionale) `coap-client` (`libcoap2-bin`) per test manuali.

### Avvio rapido del broker
Con Docker Compose è possibile avviare il broker incluso nel progetto:
```bash
docker compose up -d mosquitto
```
Il broker sarà raggiungibile su `tcp://localhost:1883`.

## ⚙️ Variabili d'ambiente
| Variabile | Descrizione | Default |
|-----------|-------------|---------|
| `MQTT_BROKER_URL` | URL del broker MQTT | `tcp://localhost:1883` |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | Credenziali opzionali per il broker | *(non impostato)* |
| `AUTO_RESET_ON_ALARM` | Se `true` il `DataCollectorManager` invia automaticamente `RESET` ai robot in stato `ALARM` | `true` |

## 🔨 Build
```bash
mvn clean package -DskipTests
```
Il comando genera `target/smart-microfactory-<version>-shaded.jar` con tutte le dipendenze.

## ▶️ Esecuzione
Assicurarsi che il broker MQTT sia attivo, quindi avviare la simulazione:
```bash
java -jar target/smart-microfactory-*-shaded.jar
```
Durante l'avvio vengono attivati:
1. `CommandPublisher` (bridge CoAP→MQTT) registrato nello `StateRepository`.
2. `DataCollectorManager` che si sottoscrive a `mf/+/+/+/status` e aggiorna il digital twin.
3. Server CoAP sulla porta `5683`.
4. Simulatori dei dispositivi (robot, conveyor, sensore) che pubblicano stato e ack.

Per interrompere il sistema usare `Ctrl+C`. Il processo arresta i simulatori, chiude le connessioni MQTT e spegne il server CoAP.

## 🌐 API CoAP
| Metodo | Risorsa | Descrizione |
|--------|---------|-------------|
| `GET` | `/factory` | Info generali sul servizio. |
| `GET` | `/factory/{cell}/devices` | Elenco dei dispositivi registrati in una cella. |
| `GET` | `/factory/{cell}/{type}/{id}/state` | Stato del dispositivo (JSON, text/plain o SenML JSON). Supporta Observe. |
| `POST` | `/factory/{cell}/{type}/{id}/cmd` | Invia un comando al dispositivo in formato JSON (`Command`). |
| `POST` | `/factory/cmd` | Comando broadcast a tutti i dispositivi. |
| `GET` | `/factory/.../cmd` | Elenco dei comandi supportati e payload di esempio. |

### Modello `Command`
```json
{
  "type": "START",
  "ts": 1710000000000
}
```
- `type`: `RESET`, `START`, `STOP` (più `EMERGENCY` per il broadcast).
- `ts`: timestamp UNIX in millisecondi (facoltativo, viene inserito automaticamente se assente).

### Risposta `Ack`
```json
{
  "cmdType": "START",
  "status": "ACCEPTED",
  "message": "Comando inoltrato al broker MQTT",
  "ts": 1710000001234
}
```
Il codice CoAP restituito è `2.04 Changed` in caso di inoltro corretto, `4.00 Bad Request` per payload non validi, `4.06 Not Acceptable` per Content-Format errato e `5.03 Service Unavailable` se il broker MQTT non è raggiungibile.

## 📡 MQTT Topics
| Topic | Direzione | Descrizione |
|-------|-----------|-------------|
| `mf/<cell>/<type>/<id>/status` | Dispositivo → Broker | Telemetria periodica (JSON). |
| `mf/<cell>/<type>/<id>/cmd` | Broker → Dispositivo | Comandi inoltrati da CoAP (payload `Command`). |
| `mf/<cell>/<type>/<id>/ack` | Dispositivo → Broker | Riscontro del comando (`Ack`). |
| `mf/broadcast/cmd` | Broker → Tutti | Comandi globali (payload `Command`). |

È possibile osservare i messaggi, ad esempio:
```bash
mosquitto_sub -h localhost -t 'mf/+/+/+/cmd' -v
```

## 🧪 Testing
- **Test automatici CoAP**: `./coap_test_suite_fixed.sh` (richiede `coap-client`). Lo script invia comandi con il nuovo modello `Command`.
- **Piano di test completo**: consultare [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) per casi di prova dettagliati su CoAP e MQTT (comandi `RESET`, `START`, `STOP`, `EMERGENCY`, gestione errori, osservazione dello stato, ack, LWT).
- **Test manuali**:
  ```bash
  # Stato robot
  coap-client -m get coap://localhost:5683/factory/cell-01/robot/robot-001/state

  # Invio comando START
  coap-client -m post -t 50 -e '{"type":"START"}' \
    coap://localhost:5683/factory/cell-01/robot/robot-001/cmd

  # Comando globale di STOP
  coap-client -m post -t 50 -e '{"type":"STOP"}' coap://localhost:5683/factory/cmd
  ```

## 📁 Struttura del progetto (principali package)
- `adapters.coap`: server CoAP e risorse.
- `communication.mqtt`: utility per la pubblicazione (client device + `CommandPublisher`).
- `device.simulator`: simulatori MQTT dei dispositivi.
- `domain`: `StateRepository` (digital twin + bridging).
- `manager`: `DataCollectorManager` (MQTT subscriber).
- `model`: POJO condivisi (`Command`, `Ack`, stati dispositivo).
- `util`: helper vari (SenML, content format).

## 📝 Ulteriori note
- I comandi non supportati restituiscono un messaggio dettagliato con la lista delle azioni ammesse.
- Se il broker MQTT non è disponibile, l'API CoAP segnala `5.03 Service Unavailable` senza perdere il comando.
- Tutti i payload CoAP di comando usano esclusivamente JSON per garantire coerenza con il modello `Command/Ack` e con gli altri protocolli.
