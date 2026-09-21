# Multi-stage build for QueuePilot
FROM gradle:9.1.0-jdk17 AS builder

WORKDIR /app
COPY . .

RUN gradle build -x test --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.awt.headless=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
