# Piano di Test Smart Microfactory IoT

Questo documento raccoglie i test consigliati (manuali e semi-automatici) per verificare il corretto funzionamento della piattaforma dopo le ultime modifiche. Le prove sono suddivise per area funzionale e includono i comandi MQTT/CoAP più rilevanti (`RESET`, `START`, `STOP`, `EMERGENCY`).

## 1. Preparazione ambiente
1. Avviare il broker MQTT (es. `docker compose up -d mosquitto`).
2. Avviare la simulazione: `java -jar target/smart-microfactory-*-shaded.jar`.
3. Facoltativo: avviare una sessione di monitoraggio MQTT
   ```bash
   mosquitto_sub -h localhost -t 'mf/#' -v
   ```
4. Tenere a portata di mano `coap-client` per i test CoAP.

## 2. Test API CoAP
### 2.1 Discovery e risorse di stato
- `GET coap://localhost:5683/.well-known/core` → verificare che siano esposte `/factory`, `/factory/cmd` e risorse dinamiche.
- `GET coap://localhost:5683/factory/cell-01/devices` → controllare che l'elenco includa robot, conveyor e sensore.
- `GET coap://localhost:5683/factory/cell-01/robot/robot-001/state` in:
  - JSON (default)
  - `-A 0` text/plain
  - `-A 110` SenML JSON
- Avviare un observe: `coap-client -m get -s 30 -B 5 -s 60 -O 6 coap://localhost:5683/factory/cell-01/robot/robot-001/state` e verificare gli aggiornamenti periodici.

### 2.2 Comandi dispositivo (`/cmd`)
Per ogni comando usare payload `Command` JSON e verificare la risposta `Ack`.

| Comando | Payload | Atteso |
|---------|---------|--------|
| `RESET` | `{"type":"RESET"}` | Robot torna in stato `IDLE`, ack `ACCEPTED`. |
| `START` | `{"type":"START"}` | Se robot in `IDLE`, passa a `PROCESSING`; ack `ACCEPTED`. |
| `STOP`  | `{"type":"STOP"}`  | Se robot in `PROCESSING`, torna a `IDLE`; ack `ACCEPTED`. |

Esempio comando:
```bash
coap-client -m post -t 50 -e '{"type":"START"}' \
  coap://localhost:5683/factory/cell-01/robot/robot-001/cmd
```
Controllare su MQTT l'emissione di `mf/cell-01/robot/robot-001/cmd` con il medesimo payload e la ricezione di `mf/.../ack`.

### 2.3 Comandi globali
- `POST` `{"type":"STOP"}` su `/factory/cmd` → devono essere pubblicati `STOP` su tutti i topic `mf/<cell>/<type>/<id>/cmd` e generati ack.
- `POST` `{"type":"EMERGENCY"}` → controllare l'invio sul topic `mf/broadcast/cmd`.

### 2.4 Gestione errori
- Content-Format errato (`-t 0` con payload JSON) → risposta `4.06 Not Acceptable`.
- Payload senza `type` → risposta `4.00 Bad Request` con messaggio descrittivo.
- Comando non supportato (`{"type":"PAUSE"}`) → `4.00 Bad Request` con elenco comandi ammessi.
- Spegnere il broker MQTT e ripetere un comando → risposta `5.03 Service Unavailable`.

## 3. Test MQTT diretti
### 3.1 Telemetria
- `mosquitto_sub -t 'mf/+/+/+/status' -v` → verificare pubblicazioni periodiche da robot, conveyor e quality sensor.

### 3.2 Comandi
- Pubblicare manualmente `{"type":"START"}` su `mf/cell-01/robot/robot-001/cmd` → robot deve reagire e pubblicare ack.
- Pubblicare `{"type":"RESET"}` su `mf/broadcast/cmd` → ogni dispositivo deve riceverlo (i robot rispondono con ack `RESET`).

### 3.3 Last Will & Testament
- Interrompere forzatamente un simulatore (es. kill del thread) → verificare la pubblicazione retained `offline` sul topic `mf/<cell>/<type>/<id>/lwt`.

## 4. DataCollector & Auto Reset
1. Forzare manualmente un `ALARM` sul robot pubblicando su MQTT uno stato con `status="ALARM"`.
2. Il `DataCollectorManager` deve rilevare l'evento e pubblicare automaticamente `{"type":"RESET"}` su `mf/cell-01/robot/<id>/cmd`.
3. Verificare che lo stato torni a `IDLE` e che l'ack sia `RESET/ACCEPTED`.

## 5. Test di regressione
- Eseguire `./coap_test_suite_fixed.sh` e assicurarsi che tutti i test siano verdi.
- Verificare che il README rifletta le istruzioni aggiornate (build, esecuzione, comandi JSON).
- Eseguire `mvn clean package` per assicurarsi che la struttura dei package sia coerente.

## 6. Checklist finale
- [ ] Tutti i comandi CoAP generano il relativo messaggio MQTT.
- [ ] Gli ack dei dispositivi sono visibili su MQTT e riportano `ACCEPTED`/`ERROR` coerente.
- [ ] Gli Observe CoAP ricevono update quando cambia lo stato.
- [ ] Le risposte di errore sono significative (content-type, comando non supportato, broker offline).
- [ ] Il piano di test viene aggiornato in caso di nuovi dispositivi o comandi.

> Suggerimento: mantenere questo documento sincronizzato con gli script di test automatici e con il README per facilitare la consegna.
