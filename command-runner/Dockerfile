FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
ARG KUBECTL_VERSION=v1.34.11
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && arch="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && curl -fsSLo /tmp/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${arch}/kubectl" \
    && curl -fsSLo /tmp/kubectl.sha256 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${arch}/kubectl.sha256" \
    && echo "$(cat /tmp/kubectl.sha256)  /tmp/kubectl" | sha256sum --check \
    && install -m 0755 /tmp/kubectl /usr/local/bin/kubectl && rm -rf /tmp/kubectl* /var/lib/apt/lists/* \
    && groupadd --system aiops && useradd --system --gid aiops --home-dir /app aiops
WORKDIR /app
COPY --from=build --chown=aiops:aiops /workspace/target/klueops-command-runner-*.jar app.jar
USER 999:999
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
