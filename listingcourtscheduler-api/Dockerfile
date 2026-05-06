# ---- Base image (default fallback) ----
ARG BASE_IMAGE
FROM ${BASE_IMAGE:-eclipse-temurin:25-jdk}

# ---- Build/runtime arguments ----
ARG SERVER_PORT
ARG JAR_FILENAME
ARG JAR_FILE_PATH
ARG CP_BACKEND_URL
ARG CJSCPPUID
ARG CERTS_DIR

ENV JAR_FILENAME=${JAR_FILENAME:-app.jar}
ENV JAR_FILE_PATH=${JAR_FILE_PATH:-build/libs}
ENV JAR_FULL_PATH=$JAR_FILE_PATH/$JAR_FILENAME

ENV CP_BACKEND_URL=$CP_BACKEND_URL
ENV CJSCPPUID=$CJSCPPUID

# ---- Set runtime ENV for Spring Boot to bind port ----
ENV SERVER_PORT=${SERVER_PORT:-8083}

# ---- Dependencies ----
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ---- Optional CPP CA certificates ----
# CERTS_DIR is the path on the build context that contains the CA pem files.
# When omitted, the COPY/keytool steps are skipped via shell guarding so this
# Dockerfile remains usable in environments without the cert bundle.
COPY ${CERTS_DIR:-./}/ /tmp/certs-stage/

RUN if [ -d /tmp/certs-stage ] && ls /tmp/certs-stage/*.pem >/dev/null 2>&1; then \
        for pem in /tmp/certs-stage/*.pem; do \
            base=$(basename "$pem" .pem); \
            cp "$pem" /usr/local/share/ca-certificates/${base}.crt; \
            keytool -importcert -trustcacerts -cacerts \
                -file "/usr/local/share/ca-certificates/${base}.crt" \
                -alias "${base}" -storepass changeit -noprompt || true; \
        done; \
        update-ca-certificates; \
    fi \
    && rm -rf /tmp/certs-stage

# ---- App user ----
RUN groupadd --system app \
 && useradd --system --gid app --home /app --shell /usr/sbin/nologin app \
 && mkdir -p /app \
 && chown -R app:app /app

WORKDIR /app

# ---- Copy the Boot fat jar ----
COPY ${JAR_FILE_PATH}/${JAR_FILENAME} /app/${JAR_FILENAME}
RUN chown app:app /app/${JAR_FILENAME}

EXPOSE ${SERVER_PORT}

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+AlwaysActAsServerClassMachine"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/${JAR_FILENAME}"]
