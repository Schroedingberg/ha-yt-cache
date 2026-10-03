# Local development helpers for the YouTube to Media app.
#
# These run the app in Docker directly, bypassing the Home Assistant
# Supervisor, so you can iterate quickly without the supervisor's
# rebuild/install dance. The committed config.yaml keeps its `image:`
# field for GHCR publishing — the Dockerfile build below is unaffected
# by that field.
#
# The PO-token provider layer (canvas compile) is cached by Docker, so
# day-to-day code changes rebuild in seconds, not minutes.

APP_DIR := youtube-to-media
IMAGE   := yt2m-dev
SHARE   ?= /mnt/supervisor/share

.PHONY: build run logs stop

build: ## Build the dev image
	docker build -t $(IMAGE) $(APP_DIR)

run: ## Build and run the app on http://localhost:8099
	$(MAKE) build
	-docker rm -f yt2m-dev
	docker run -d --name yt2m-dev -p 8099:8099 \
		-v "$(SHARE):/share" \
		-e DOWNLOAD_DIRECTORY=/share/youtube-to-media \
		-e PORT=8099 \
		$(IMAGE)

logs: ## Follow the dev container logs
	docker logs -f yt2m-dev

stop: ## Stop and remove the dev container
	-docker rm -f yt2m-dev
