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

LABEL \
    org.opencontainers.image.title="Home Assistant App: YouTube to Media" \
    org.opencontainers.image.description="Download YouTube media to Home Assistant shared storage." \
    org.opencontainers.image.source="https://github.com/Schroedingberg/ha-yt-cache" \
    org.opencontainers.image.licenses="Apache License 2.0"


# Install babashka (static binary from GitHub releases, pinned for reproducibility)
ARG BABASHKA_VERSION=1.13.225
RUN case "${TARGETARCH}" in \
      amd64) bb_arch="amd64" ;; \
      arm64) bb_arch="aarch64" ;; \
      *) echo "Unsupported TARGETARCH: ${TARGETARCH}" && exit 1 ;; \
    esac \
    && set -e \
    && for i in 1 2 3 4 5; do \
         curl -sSLf -o /tmp/bb.tar.gz \
           "https://github.com/babashka/babashka/releases/download/v${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION}-linux-${bb_arch}-static.tar.gz" && break; \
         echo "babashka download attempt ${i} failed, retrying..."; sleep 5; \
       done \
    && test -s /tmp/bb.tar.gz \
    && tar -xzf /tmp/bb.tar.gz -C /usr/local/bin \
    && rm -f /tmp/bb.tar.gz \
    && bb --version

RUN apk add --no-cache git nodejs npm ffmpeg uv

# Install the bgutil yt-dlp PO token provider (Node.js).
# The native `canvas` dependency has no musl prebuild, so install its
# build-time libraries for compiling from source on Alpine.
RUN apk add --no-cache build-base python3 pkgconf cairo-dev pango-dev pixman-dev \
    && set -e \
    && for i in 1 2 3 4 5; do \
         git clone --single-branch --branch 2.0.1 \
           https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git \
           /usr/share/bgutil-ytdlp-pot-provider && break; \
         echo "bgutil clone attempt ${i} failed, retrying..."; sleep 5; \
       done \
    && test -d /usr/share/bgutil-ytdlp-pot-provider/server \
    && cd /usr/share/bgutil-ytdlp-pot-provider/server \
    && npm ci --no-audit --no-fund \
    && npx tsc

# Copy root filesystem and application source
COPY rootfs /
COPY bb.edn /usr/share/youtube-to-media/
COPY src /usr/share/youtube-to-media/src/

# Verify the Babashka application loads
RUN cd /usr/share/youtube-to-media && bb -e "(require 'youtube-to-media.core)"