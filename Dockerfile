FROM alpine:3.22.1 AS ddjava
ARG DD_JAVA_AGENT_VERSION=1.52.1
RUN apk add --no-cache wget && \
    wget -O /dd-java-agent.jar \
    https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/${DD_JAVA_AGENT_VERSION}/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar && \
    ls -la /dd-java-agent.jar

FROM amazoncorretto:21 AS build
WORKDIR /workspace/app

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

RUN ./gradlew --no-daemon -q dependencies || true

COPY src ./src

RUN ./gradlew --no-daemon clean bootJar

FROM amazoncorretto:21
WORKDIR /app

COPY --from=ddjava /dd-java-agent.jar /app/dd-java-agent.jar
RUN (command -v dnf >/dev/null 2>&1 && dnf install -y curl && dnf clean all) \
 || (command -v yum >/dev/null 2>&1 && yum install -y curl && yum clean all) \
 || (command -v microdnf >/dev/null 2>&1 && microdnf install -y curl && microdnf clean all) \
 || (command -v apk >/dev/null 2>&1 && apk add --no-cache curl) \
 || (command -v apt-get >/dev/null 2>&1 && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*) \
 || true

COPY --from=build /workspace/app/build/libs/*.jar /app/
RUN set -e; jar=$(ls /app/*.jar | grep -v 'plain.jar' | head -n 1); mv "$jar" /app/app.jar; rm -f /app/*-plain.jar

# Copy the secrets loading script
COPY load-secrets.sh /app/load-secrets.sh
RUN chmod +x /app/load-secrets.sh

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/dd-java-agent.jar ${JAVA_TOOL_OPTIONS:-}"
EXPOSE 8080

ENTRYPOINT ["/app/load-secrets.sh"]
