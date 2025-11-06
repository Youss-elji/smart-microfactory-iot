FROM ubuntu:latest
LABEL authors="Youssef"

ENTRYPOINT ["top", "-b"]

# Build multi-stage per ridurre la dimensione dell'immagine finale
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# Copia il pom.xml e scarica le dipendenze sfruttando la cache
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia il sorgente e compila il progetto
COPY src ./src
RUN mvn clean package -DskipTests

# Fase runtime
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="youssefeljihad84@gmail.com"
LABEL description="Smart Microfactory IoT System"

WORKDIR /app

# Copia l'artefatto JAR dalla fase di build
COPY --from=build /build/target/smart-microfactory-*-shaded.jar app.jar

# Variabili d'ambiente con valori di default
ENV MQTT_BROKER_URL=tcp://mosquitto:1883
ENV MQTT_USERNAME=""
ENV MQTT_PASSWORD=""
ENV COAP_PORT=5683
ENV AUTO_RESET_ON_ALARM=true
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Espone la porta CoAP (UDP)
EXPOSE 5683/udp

# Controllo di salute del container
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Avvia l'applicazione
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]