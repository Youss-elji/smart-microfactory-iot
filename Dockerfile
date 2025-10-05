FROM ubuntu:latest
LABEL authors="Youssef"

ENTRYPOINT ["top", "-b"]

# Multi-stage build for smaller final image
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="youssefeljihad84@gmail.com"
LABEL description="Smart Microfactory IoT System"

WORKDIR /app

# Copy the shaded JAR from build stage
COPY --from=build /build/target/smart-microfactory-*-shaded.jar app.jar

# Environment variables with defaults
ENV MQTT_BROKER_URL=tcp://mosquitto:1883
ENV MQTT_USERNAME=""
ENV MQTT_PASSWORD=""
ENV COAP_PORT=5683
ENV AUTO_RESET_ON_ALARM=true
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expose CoAP port (UDP)
EXPOSE 5683/udp

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]