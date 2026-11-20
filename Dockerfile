# Share Ubuntu's security-patched JRE between the builder and the runtime.
FROM ubuntu:22.04 AS java

ENV LANG=C.UTF-8

# Ubuntu USN-9156-1 fixes CVE-2026-114938 and other OpenJDK 21 CVEs.
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      ca-certificates \
      openjdk-21-jre-headless \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' openjdk-21-jre-headless)" ge '21.0.13.1+2-1~22.04.1' \
    && rm -rf /var/lib/apt/lists/*

# ============================================
# Build the Spring Boot application
# ============================================
FROM java AS build

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

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
# Runtime image (Ubuntu Jammy for Tesseract)
# ============================================
FROM java AS runtime

WORKDIR /app

# Pin Datadog Java agent version
ARG DD_JAVA_AGENT_VERSION=1.52.1

# Install runtime dependencies including Tesseract OCR and Leptonica
# Use PPA for newer Tesseract/Leptonica versions that match lept4j ABI expectations
RUN apt-get update && apt-get install -y --no-install-recommends \
      gnupg \
      software-properties-common \
    && add-apt-repository -y ppa:alex-p/tesseract-ocr-devel \
    && apt-get update && apt-get install -y --no-install-recommends \
      curl \
      fontconfig \
      fonts-dejavu-core \
      gosu \
      tesseract-ocr \
      tesseract-ocr-eng \
      tesseract-ocr-osd \
      libtesseract-dev \
      libleptonica-dev \
    && ln -sf /usr/lib/x86_64-linux-gnu/liblept.so /usr/lib/x86_64-linux-gnu/libleptonica.so \
    && ldconfig \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -u 10001 -m -s /bin/sh appuser

# Verify Tesseract installation and detect tessdata directory
# Using tess4j 5.9.0 which is compatible with Ubuntu 22.04's Leptonica 1.82.0
# Tesseract 5.x from PPA uses /usr/share/tesseract-ocr/5/tessdata
# NOTE: Detection runs at BUILD TIME and writes result for RUNTIME use
RUN set -eux; \
    echo "=== Tesseract version ==="; \
    tesseract --version 2>&1 | head -3; \
    echo ""; \
    echo "=== Installed Leptonica version ==="; \
    dpkg -l | grep leptonica || true; \
    echo ""; \
    echo "=== Detecting tessdata directory ==="; \
    if [ -f "/usr/share/tesseract-ocr/5/tessdata/eng.traineddata" ]; then \
      detectedTessdataDirectory="/usr/share/tesseract-ocr/5/tessdata"; \
    elif [ -f "/usr/share/tesseract-ocr/4.00/tessdata/eng.traineddata" ]; then \
      detectedTessdataDirectory="/usr/share/tesseract-ocr/4.00/tessdata"; \
    elif [ -f "/usr/share/tessdata/eng.traineddata" ]; then \
      detectedTessdataDirectory="/usr/share/tessdata"; \
    else \
      echo "ERROR: eng.traineddata not found in any standard location"; \
      echo "Searched paths:"; \
      ls -la /usr/share/tesseract-ocr/ || true; \
      ls -la /usr/share/tessdata/ 2>/dev/null || true; \
      exit 1; \
    fi; \
    echo "✓ Found tessdata directory: $detectedTessdataDirectory"; \
    ls -l "$detectedTessdataDirectory/eng.traineddata"; \
    echo ""; \
    echo "=== Persisting detected path for runtime ==="; \
    mkdir -p /etc/profile.d; \
    echo "export TESSDATA_PREFIX=$detectedTessdataDirectory" > /etc/profile.d/tessdata.sh; \
    chmod 644 /etc/profile.d/tessdata.sh; \
    echo ""; \
    echo "=== Configuration Summary ==="; \
    echo "Tesseract: $(tesseract --version 2>&1 | head -1)"; \
    echo "Leptonica: $(dpkg -l | grep libleptonica | awk '{print $3}')"; \
    echo "Tessdata: $detectedTessdataDirectory"; \
    echo "Using tess4j 5.9.0 (compatible with Leptonica 1.82.0)"

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
# Tesseract 5.x from alex-p PPA uses /usr/share/tesseract-ocr/5/tessdata
# Tess4J expects the directory that contains *.traineddata files
# NOTE: docker-compose.yml can override this if needed
ENV OMP_THREAD_LIMIT=1
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

# Free-tier friendly memory settings (prevents OOM on t3.micro/small)
# -Djna.nosys=false ensures JNA uses tmpdir even in read-only filesystem (critical for standalone runs)
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/dd-java-agent.jar -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.io.tmpdir=/tmp -Djna.tmpdir=/tmp -Djna.nosys=false"
ENV HOME=/tmp

EXPOSE 8080

# Start as root to read secrets, then drop privileges in entrypoint
ENTRYPOINT ["/app/load-secrets.sh"]

