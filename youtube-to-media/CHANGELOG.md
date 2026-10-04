<!-- https://developers.home-assistant.io/docs/apps/presentation#keeping-a-changelog -->

## 0.2.0

- Fixed a deadlock that could hang long downloads.
- Downloads now run in the order they were added.
- Supervisor notifications time out instead of hanging a job.
- Reject oversized enqueue request bodies.
- Added automated tests and linting in CI.

## 0.1.0

- Download YouTube media into Home Assistant shared storage.
- Web UI served through Home Assistant ingress (authenticated).
- Downloads run through yt-dlp with a proof-of-origin (PO) token provider to avoid YouTube HTTP 403 errors.
