# 🏭 Smart Microfactory IoT - Server CoAP

## 📋 Descrizione
Server CoAP per il controllo e monitoraggio di una microfactory intelligente.
Implementa il pattern REST su protocollo CoAP per dispositivi IoT a basso consumo.

## 🚀 Installazione

### Prerequisiti
- Java 17+
- Maven 3.6+
- (Opzionale) `coap-client` per testing (es. `libcoap2-bin` su Debian/Ubuntu)

### Compilazione
```bash
# Eseguire dalla root del progetto
mvn clean package -DskipTests
```

## 🏃 Esecuzione

### Prerequisito
Assicurarsi che un broker MQTT sia in esecuzione (es. Mosquitto su `tcp://localhost:1883`).

### Avvio del Server
Una volta compilato il progetto, eseguire il JAR "shaded" che contiene tutte le dipendenze.

#### Linux/Mac/WSL
```bash
java -jar target/smart-microfactory-*-shaded.jar
```

#### Windows (PowerShell/CMD)
```powershell
java -jar target\smart-microfactory-*-shaded.jar
```

Il server si avvierà e si metterà in ascolto sulla porta CoAP (default: `5683`).

## 🧪 Testing

### Test Automatici
Lo script `coap_test_suite_fixed.sh` esegue una serie di test di fumo per verificare gli endpoint principali.

#### Linux/Mac/WSL
```bash
# Rendi lo script eseguibile
chmod +x coap_test_suite_fixed.sh

# Esegui i test
./coap_test_suite_fixed.sh
```

#### Windows (con Git Bash o WSL)
```bash
# Esegui lo script tramite sh
sh coap_test_suite_fixed.sh
```

### Test Manuali con `coap-client`
È possibile interagire manualmente con gli endpoint usando `coap-client`.

```bash
# GET stato fabbrica (endpoint di base)
coap-client -m get coap://localhost:5683/factory

# GET stato di un dispositivo specifico (es. robot-001 in cell-01)
coap-client -m get coap://localhost:5683/factory/cell-01/robot/robot-001/state

# POST comando a un dispositivo
# Invia il comando START al robot-001
echo '{"cmd":"START"}' | coap-client -m post -T "application/json" -f - coap://localhost:5683/factory/cell-01/robot/robot-001/cmd
```

## 📊 Output Atteso

### Stato Fabbrica (GET /factory)
Un semplice JSON che descrive lo stato generale del servizio.
```json
{
  "name": "smart-microfactory",
  "status": "operational",
  "version": "1.0"
}
```

### Stato Dispositivo (GET /factory/{cell}/{type}/{id}/state)
Esempio di risposta per un robot, in formato JSON.
```json
{
  "deviceId": "robot-001",
  "timestamp": 1673346600000,
  "status": "IDLE",
  "processingTime": 0.0
}
```

### Risposta a un Comando (POST .../cmd)
Una risposta `2.04 Changed` senza corpo indica che il comando è stato accettato.

## 🏗️ Architettura

Il server CoAP espone dinamicamente le risorse in base alla gerarchia della fabbrica.

```
┌─────────────────────────────────┐
│      Client CoAP (es. Tester)   │
└─────────────┬───────────────────┘
              │
              │ CoAP (UDP:5683)
              │
┌─────────────▼───────────────────┐
│        CoapApiServer            │
├─────────────────────────────────┤
│  Risorse Dinamiche:             │
│  - /factory                     │
│    └─ /{cellId}                 │
│       ├─ /devices               │
│       └─ /{deviceType}          │
│          └─ /{deviceId}         │
│             ├─ /state (GET, OBS)│
│             └─ /cmd (POST, GET) │
└─────────────┬───────────────────┘
              │
┌─────────────▼───────────────────┐
│     StateRepository             │
│ (Digital Twin della fabbrica)   │
└─────────────────────────────────┘
```

## 📝 Note Implementative
- **Framework CoAP**: Eclipse Californium.
- **Routing Dinamico**: Le risorse vengono create al volo navigando l'URI. Ad esempio, una richiesta a `/factory/cell-01` istanzia una `CellResource` con `name="cell-01"`.
- **Pattern Observer**: La risorsa `DeviceStateResource` è osservabile (`Observable`). Si registra come listener sullo `StateRepository` e notifica i client CoAP quando lo stato del dispositivo cambia.
- **Thread-Safety**: La gestione dello stato è demandata allo `StateRepository`, che deve essere thread-safe per gestire accessi concorrenti da MQTT e CoAP.