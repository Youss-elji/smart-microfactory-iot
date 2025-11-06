# Piano di Test Smart Microfactory IoT

Questo documento raccoglie i test consigliati (manuali e semi-automatici) per verificare il corretto funzionamento della piattaforma dopo le ultime modifiche. Le prove sono suddivise per area funzionale e includono i comandi MQTT/CoAP più rilevanti (`RESET`, `START`, `STOP`, `EMERGENCY`) e la gestione del campo `msgId`.

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
Il payload dei comandi deve rispettare la struttura JSON `{"type":"<COMANDO>", "ts": <timestamp_ms>, "msgId": "<id univoco>"}`:
- `ts` è un timestamp Unix in millisecondi. È facoltativo ma consigliato per la corretta ordinazione degli eventi.
- `msgId` identifica in modo univoco il comando e **deve essere riprodotto nell'ACK** per consentire la correlazione. Gli ack privi di `msgId` devono essere considerati un fallback temporaneo (vedere § 2.5).

Per ogni comando inviare il payload JSON su `/factory/cell-01/robot/robot-001/cmd` e verificare la risposta `Ack`.

| Comando      | Stato iniziale dispositivo | Payload esempio                                                                 | Transizione attesa                                                                                              | Stato ACK  | Messaggi attesi |
|--------------|----------------------------|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|------------|-----------------|
| `RESET`      | `ALARM` o `ERROR`           | `{"type":"RESET", "ts": 1693497600000, "msgId": "reset-001"}`             | Il robot torna a `IDLE`; vengono ripristinate le code di job pendenti.                                          | `ACCEPTED` | Messaggio di reset completato. |
| `START`      | `IDLE`                      | `{"type":"START", "ts": 1693497601000, "msgId": "start-001"}`             | Il robot passa a `PROCESSING` ed emette un job in produzione.                                                    | `ACCEPTED` | Messaggio di job avviato. |
| `STOP`       | `PROCESSING`                | `{"type":"STOP", "ts": 1693497602000, "msgId": "stop-001"}`              | Il robot completa il ciclo corrente e ritorna a `IDLE`.                                                          | `ACCEPTED` | Messaggio di arresto eseguito. |
| `EMERGENCY`  | Qualsiasi (tipicamente `PROCESSING`) | `{"type":"EMERGENCY", "ts": 1693497603000, "msgId": "emerg-001"}` | Il robot interrompe immediatamente le operazioni e passa a `ALARM` fino a nuovo `RESET`.                        | `ACCEPTED` | Messaggio di emergenza diffuso. |

Esempio comando:
```bash
coap-client -m post -t 50 -e '{"type":"START", "msgId":"start-coap-01"}' \
  coap://localhost:5683/factory/cell-01/robot/robot-001/cmd
```
Controllare su MQTT l'emissione di `mf/cell-01/robot/robot-001/cmd` con il medesimo payload e la ricezione di `mf/.../ack` con lo stesso `msgId`.

### 2.3 Comandi globali
- `POST` `{"type":"STOP", "msgId":"stop-all-01"}` su `/factory/cmd` → devono essere pubblicati `STOP` su tutti i topic `mf/<cell>/<type>/<id>/cmd` e generati ack `ACCEPTED` con `msgId` identico.
- `POST` `{"type":"EMERGENCY", "msgId":"emerg-broadcast-01"}` → controllare l'invio sul topic `mf/broadcast/cmd`, la propagazione ai dispositivi e gli ack coerenti.

### 2.4 Gestione errori
- Content-Format errato (`-t 0` con payload JSON) → risposta `4.06 Not Acceptable`.
- Payload senza `type` o con campi mal formati → risposta `4.00 Bad Request` con messaggio descrittivo.
- Comando non supportato (`{"type":"PAUSE"}`) → `4.00 Bad Request` con elenco dei comandi ammessi (`RESET`, `START`, `STOP`, `EMERGENCY`).
- Spegnere il broker MQTT e ripetere un comando → risposta `5.03 Service Unavailable`.

### 2.5 Verifica `msgId`
1. Inviare un comando con `msgId` noto (es. `start-coap-msg-01`).
2. Verificare che l'ACK CoAP riporti lo stesso `msgId` nel payload (es. campo `msgId` o equivalente).
3. Se il componente non riproduce ancora `msgId`, annotare l'anomalia: il sistema deve usare il `ts` come fallback temporaneo. Segnalare il comportamento come da correggere nei rilasci successivi.

## 3. Test MQTT diretti
### 3.1 Telemetria
- `mosquitto_sub -t 'mf/+/+/+/status' -v` → verificare pubblicazioni periodiche da robot, conveyor e quality sensor.

### 3.2 Comandi
- Pubblicare manualmente `{"type":"START", "msgId":"start-mqtt-01"}` su `mf/cell-01/robot/robot-001/cmd` → il robot deve reagire, generare ack `ACCEPTED` con lo stesso `msgId` e cambiare stato in `PROCESSING`.
- Pubblicare `{"type":"RESET", "msgId":"reset-broadcast-01"}` su `mf/broadcast/cmd` → ogni dispositivo deve riceverlo e rispondere con ack `ACCEPTED` che ripete `msgId`. Verificare il ritorno a `IDLE` dei robot.

### 3.3 Last Will & Testament
- Interrompere forzatamente un simulatore (es. kill del thread) → verificare la pubblicazione retained `offline` sul topic `mf/<cell>/<type>/<id>/lwt`.

## 4. DataCollector & Auto Reset
1. Forzare manualmente un `ALARM` sul robot pubblicando su MQTT uno stato con `status="ALARM"`.
2. Impostare `AUTO_RESET_ON_ALARM=true` (variabile d'ambiente o configurazione applicativa).
3. Il `DataCollectorManager` deve rilevare l'evento e pubblicare automaticamente `{"type":"RESET", "msgId":"auto-reset-01"}` su `mf/cell-01/robot/<id>/cmd`.
4. Verificare che lo stato torni a `IDLE` e che l'ack riporti `ACCEPTED` e lo stesso `msgId`. In assenza di `msgId` nell'ack, verificare che almeno il `ts` corrisponda al comando inviato dal DataCollector.

## 5. Test di regressione
- Eseguire `./coap_test_suite_fixed.sh` e assicurarsi che tutti i test siano verdi.
- Verificare che il README rifletta le istruzioni aggiornate (build, esecuzione, comandi JSON).
- Eseguire `mvn clean package` per assicurarsi che la struttura dei package sia coerente.

## 6. Checklist finale
- [ ] Tutti i comandi CoAP generano il relativo messaggio MQTT con payload coerente (`type`, `ts`, `msgId`).
- [ ] Gli ack dei dispositivi sono visibili su MQTT e riportano `ACCEPTED`/`ERROR` coerente con lo stato, replicando `msgId` o segnalando il fallback su `ts`.
- [ ] Gli Observe CoAP ricevono update quando cambia lo stato.
- [ ] Le risposte di errore sono significative (content-type, comando non supportato, broker offline).
- [ ] Il piano di test viene aggiornato in caso di nuovi dispositivi, comandi o campi obbligatori (es. `msgId`).

> Suggerimento: mantenere questo documento sincronizzato con gli script di test automatici e con il README per facilitare la consegna.
