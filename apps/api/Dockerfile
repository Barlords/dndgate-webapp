# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Run
FROM eclipse-temurin:21-jre-alpine AS deploy

# The application does not need root: a flaw in it must not give the keys of the container
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app

# The heap follows the memory limit of the container instead of the default quarter of the machine
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Probes and metrics have their own port (not published by the reverse proxy)
ENV MANAGEMENT_PORT=4001
EXPOSE 4000

# Liveness only: a database outage must restart nothing (readiness is for the orchestrator)
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/liveness" || exit 1

CMD ["java", "-jar", "app.jar"]
