FROM amazoncorretto:21 AS build
WORKDIR /workspace/app

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

RUN ./gradlew --no-daemon -q dependencies

COPY src ./src

RUN ./gradlew --no-daemon clean bootJar

FROM amazoncorretto:21
WORKDIR /app

ARG DD_JAVA_AGENT_VERSION=1.52.1

# Install tools with fallback for different Corretto base variants
RUN set -eux; \
  base_pkgs="curl ca-certificates shadow-utils util-linux"; \
  if command -v dnf >/dev/null 2>&1; then pm="dnf"; clean="dnf clean all"; \
  elif command -v microdnf >/dev/null 2>&1; then pm="microdnf"; clean="microdnf clean all"; \
  else pm="yum"; clean="yum clean all"; fi; \
  $pm -y install $base_pkgs coreutils; \
  $clean; \
  useradd -r -u 10001 appuser

# Fetch dd-java-agent from Maven Central with checksum verification
RUN set -eux; \
    base="https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/${DD_JAVA_AGENT_VERSION}"; \
    curl -fsSLo /app/dd-java-agent.jar "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar"; \
    if curl -fsSLo /tmp/dd.jar.sha256 "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar.sha256"; then \
      awk '{print $1"  /app/dd-java-agent.jar"}' /tmp/dd.jar.sha256 | sha256sum -c -; \
    else \
      curl -fsSLo /tmp/dd.jar.sha512 "$base/dd-java-agent-${DD_JAVA_AGENT_VERSION}.jar.sha512"; \
      awk '{print $1"  /app/dd-java-agent.jar"}' /tmp/dd.jar.sha512 | sha512sum -c -; \
    fi

# Copy the boot JAR directly (plain JAR is disabled in build.gradle.kts)
COPY --from=build /workspace/app/build/libs/app.jar /app/app.jar

# Copy the secrets loading script with correct permissions
COPY --chmod=0755 load-secrets.sh /app/load-secrets.sh

# Set safe HOME directory for libraries that reference it
ENV HOME=/tmp

# Don't inject the Datadog agent by default - let docker-compose control this
ENV JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-}"
EXPOSE 8080

# Start as root to read secrets, then drop privileges in entrypoint
ENTRYPOINT ["/app/load-secrets.sh"]
