# Used by the ADO `cp-gh-artifact-to-acr` pipeline ("Docker build and push to NON-LIVE")
# which expects the Dockerfile at /workspace/Dockerfile after cloning the target repo.
# The pipeline downloads the published Spring Boot fat jar from the HMCTS Azure Artifacts
# feed and lands it in build/libs/ before invoking `docker build` from the repo root.
#
# baseImage is supplied by the GH-actions Deploy step (see .github/workflows/ci-build-publish.yml):
#   "agentDemand": "ubuntu-j25", "baseImage": "hmcts/apm-services:25-jre"
# The ADO pipeline replaces $BASE_IMAGE with crmdvrepo01.azurecr.io/$baseImage. This image already
# trusts HMCTS' self-signed CA, so no extra cert wiring is needed here.
ARG BASE_IMAGE
FROM ${BASE_IMAGE:-eclipse-temurin:25-jre}

# curl is useful for in-pod debugging and for the actuator health probe baked into deployment manifests.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r app && useradd -r -g app app
WORKDIR /app

# Application files. startup.sh picks the right (non-plain) jar at runtime.
COPY docker/* /app/
COPY build/libs/*.jar /app/

USER app
ENTRYPOINT ["/bin/sh","./startup.sh"]
