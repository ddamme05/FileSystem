# ============================================
# Stage 1: Build the Spring Boot application
# ============================================
# Pin Gradle and JDK versions to prevent unexpected upgrades
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
# Stage 2: Runtime image (Ubuntu Jammy for Tesseract)
# ============================================
# Pin specific JRE version for reproducible builds
# Format: eclipse-temurin:{java_version}_{build_version}-jre-jammy
FROM eclipse-temurin:21.0.5_11-jre-jammy

WORKDIR /app

# Pin Datadog Java agent version
ARG DD_JAVA_AGENT_VERSION=1.52.1

# Install runtime dependencies including Tesseract OCR
# Note: Ubuntu 22.04 (Jammy) ships with Tesseract 4.1.1
# Tess4J 5.16.0 is compatible with Tesseract 4.x and 5.x
RUN apt-get update && apt-get install -y --no-install-recommends \
      curl \
      gosu \
      tesseract-ocr \
      tesseract-ocr-eng && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -u 10001 -m -s /bin/sh appuser

# Verify Tesseract is installed and working (4.x or 5.x)
RUN tesseract --version 2>&1 | head -1

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

# Environment variables for Tesseract
ENV OMP_THREAD_LIMIT=1
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata

# Free-tier friendly memory settings (prevents OOM on t3.micro/small)
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/dd-java-agent.jar -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
ENV HOME=/tmp

EXPOSE 8080

# Start as root to read secrets, then drop privileges in entrypoint
ENTRYPOINT ["/app/load-secrets.sh"]

