<!-- https://developers.home-assistant.io/docs/apps/presentation#keeping-a-changelog -->

## 0.1.0

- Download YouTube media into Home Assistant shared storage.
- Web UI served through Home Assistant ingress (authenticated).
- Downloads run through yt-dlp with a proof-of-origin (PO) token provider to avoid YouTube HTTP 403 errors.
