# https://developers.home-assistant.io/docs/apps/configuration#app-dockerfile
ARG BUILD_FROM=ghcr.io/home-assistant/base:3.23
FROM ${BUILD_FROM}

# Execute during the build of the image
ARG TEMPIO_VERSION=2021.09.0
ARG TARGETARCH
RUN \
    if [ -z "${TARGETARCH}" ]; then \
    echo "TARGETARCH is not set, please use Docker BuildKit for the build." && exit 1; \
    fi \
    && case "${TARGETARCH}" in \
    amd64) tempio_arch="amd64" ;; \
    arm64) tempio_arch="aarch64" ;; \
    *) echo "Unsupported TARGETARCH: ${TARGETARCH}" && exit 1 ;; \
    esac \
    && curl -sSLf -o /usr/bin/tempio \
    "https://github.com/home-assistant/tempio/releases/download/${TEMPIO_VERSION}/tempio_${tempio_arch}"

# Copy root filesystem
COPY rootfs /
COPY bb.edn /usr/share/youtube-to-media/
COPY src /usr/share/youtube-to-media/src/

LABEL \
    org.opencontainers.image.title="Home Assistant App: YouTube to Media" \
    org.opencontainers.image.description="Download YouTube media to Home Assistant shared storage." \
    org.opencontainers.image.source="https://github.com/Schroedingberg/ha-yt-cache" \
    org.opencontainers.image.licenses="Apache License 2.0"


# Install latest babashka release
RUN curl -sSLf raw.githubusercontent.com/babashka/babashka/master/install | bash
RUN cd /usr/share/youtube-to-media && bb -e "(require 'youtube-to-media.core)"

RUN apk add --no-cache deno ffmpeg uv