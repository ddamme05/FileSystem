# Multi-stage build for Spring Boot app
FROM amazoncorretto:21 AS build
WORKDIR /workspace/app

# Copy Gradle wrapper and build scripts first to leverage layer caching
COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Prime dependency cache (does not require source files)
RUN ./gradlew --no-daemon -q dependencies || true

# Now copy source code
COPY src ./src

# Build the fat jar (tests are not run for bootJar)
RUN ./gradlew --no-daemon clean bootJar

FROM amazoncorretto:21
ENV JAVA_OPTS=""
WORKDIR /app
# Install curl for healthchecks (support various base distros)
RUN (command -v dnf >/dev/null 2>&1 && dnf install -y curl && dnf clean all) \
 || (command -v yum >/dev/null 2>&1 && yum install -y curl && yum clean all) \
 || (command -v microdnf >/dev/null 2>&1 && microdnf install -y curl && microdnf clean all) \
 || (command -v apk >/dev/null 2>&1 && apk add --no-cache curl) \
 || (command -v apt-get >/dev/null 2>&1 && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*) \
 || true
COPY --from=build /workspace/app/build/libs/*.jar /app/
# Pick the bootable jar (exclude the plain jar) and rename to app.jar
RUN set -e; jar=$(ls /app/*.jar | grep -v 'plain.jar' | head -n 1); mv "$jar" /app/app.jar; rm -f /app/*-plain.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

