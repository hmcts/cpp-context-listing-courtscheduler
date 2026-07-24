# docker/Dockerfile — runtime image for integration tests
FROM eclipse-temurin:25-jdk

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app \
 && useradd --system --gid app --home /app --shell /usr/sbin/nologin app \
 && mkdir -p /app \
 && chown -R app:app /app

WORKDIR /app

# Copy the Boot fat jar (the 'plain' classifier is filtered out at runtime below)
COPY build/libs/*.jar /app/

EXPOSE ${SERVER_PORT:-8083}

ENV JAVA_OPTS="\
-XX:MaxRAMPercentage=75 \
-XX:+AlwaysActAsServerClassMachine \
-Dmanagement.tracing.enabled=false \
-Dmanagement.otlp.tracing.export.enabled=false \
-Dspring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.tracing.TracingAutoConfiguration,org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration,org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration,org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration \
"

USER app
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar $(ls /app/*.jar | grep -v 'plain' | head -n1)"]
