#!/bin/bash
set -e

# Colori per l'output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Smart Microfactory - Deployment${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. Compilazione del progetto
echo -e "${YELLOW}[1/5] Building project...${NC}"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi
echo -e "${GREEN}Build successful!${NC}"
echo ""

# 2. Arresto di un'eventuale istanza precedente
echo -e "${YELLOW}[2/5] Stopping old instance...${NC}"
pkill -f "smart-microfactory" || echo "No previous instance found"
sleep 2
echo ""

# 3. Backup dei log precedenti
echo -e "${YELLOW}[3/5] Backing up logs...${NC}"
if [ -f "app.log" ]; then
    timestamp=$(date +%Y%m%d_%H%M%S)
    mv app.log "app_backup_${timestamp}.log"
    echo "Old log backed up as app_backup_${timestamp}.log"
fi
echo ""

# 4. Avvio della nuova istanza
echo -e "${YELLOW}[4/5] Starting new instance...${NC}"
JAR=$(find target -name "*-shaded.jar" -type f | head -1)
nohup java -jar "$JAR" > app.log 2>&1 &
PID=$!
echo "Started with PID: $PID"
echo $PID > microfactory.pid
echo ""

# 5. Controllo sullo stato di avvio
echo -e "${YELLOW}[5/5] Waiting for server to start...${NC}"
sleep 10

# Verifica semplificata: controlla che il processo sia attivo
if ps -p $PID > /dev/null; then
    echo -e "${GREEN}Server is running!${NC}"
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Deployment completed successfully!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "View logs: tail -f app.log"
    echo "Stop server: kill $PID"
else
    echo -e "${RED}Server failed to start. Check app.log for errors.${NC}"
    exit 1
fi
