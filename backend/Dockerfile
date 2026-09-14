# Build the Spring Boot artifact with JDK 17; Maven never enters the runtime image.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# Run only the packaged jar on a smaller JRE image as a numeric non-root user.
FROM eclipse-temurin:17-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system aiops \
    && useradd --system --gid aiops --home-dir /app aiops
WORKDIR /app
COPY --from=build --chown=aiops:aiops /workspace/target/klueops-backend-*.jar app.jar
USER 999:999
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
