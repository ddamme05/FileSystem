# ============================================
# Stage 1: Build the Spring Boot application
# ============================================
FROM gradle:8.8-jdk21-alpine AS build

WORKDIR /src

# Copy dependency files first (for layer caching)
COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew --no-daemon -q dependencies

# Copy source and build
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ============================================
# Stage 2: Runtime image
# ============================================
FROM amazoncorretto:21-alpine

WORKDIR /app

ARG DD_JAVA_AGENT_VERSION=1.52.1

# Install runtime dependencies (curl for healthchecks, su-exec for privilege dropping)
RUN apk add --no-cache curl su-exec && \
    adduser -D -u 10001 -s /bin/sh appuser

# Copy the boot JAR from build stage
COPY --from=build /src/build/libs/app.jar /app/app.jar

# Copy the secrets loading script with correct permissions
COPY --chmod=0755 load-secrets.sh /app/load-secrets.sh

# Optional: Fetch Datadog agent (for monitoring)
RUN set -eux; \
    base="https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/${DD_JAVA_AGENT_VERSION}"; \
    curl -fsSLo /app/dd-java-agent.jar "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar"; \
    if curl -fsSLo /tmp/dd.jar.sha256 "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar.sha256"; then \
      awk '{print $1"  /app/dd-java-agent.jar"}' /tmp/dd.jar.sha256 | sha256sum -c -; \
    else \
      curl -fsSLo /tmp/dd.jar.sha512 "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar.sha512"; \
      awk '{print $1"  /app/dd-java-agent.jar"}' /tmp/dd.jar.sha512 | sha512sum -c -; \
    fi || echo "Datadog agent download failed, continuing without it"

# Free-tier friendly memory settings (prevents OOM on t3.micro/small)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+UseSerialGC"
ENV HOME=/tmp

EXPOSE 8080

# Start as root to read secrets, then drop privileges in entrypoint
ENTRYPOINT ["/app/load-secrets.sh"]
