# YouTube to Media Home Assistant App

> WARNING: Don't rely on this yet. This is mostly a proof of concept for developing homeassistant apps in babashka. Use it at your own risk.

Downloads YouTube media into Home Assistant's persistent `/share/youtube-to-media` directory.

See the [app documentation](./youtube-to-media/DOCS.md) for installation and access details.

## Development

Run the VS Code task **Development: Start Local Server** to execute the working tree directly on port `8098`. Calva Jack-in uses the Babashka project in `youtube-to-media/`.

## Production / Publishing

`youtube-to-media/config.yaml` points to `ghcr.io/schroedingberg/youtube-to-media`. Pull requests build without publishing; pushes to `main` build the multi-arch image and publish versioned and `latest` tags to GHCR via the Builder workflow.

After the first publish, make the GHCR package public so Home Assistant can pull it.

## Installing

1. In Home Assistant, open **Settings → Apps** and add this repository:
   `https://github.com/Schroedingberg/ha-yt-cache`
2. Install **YouTube to Media** and open its web UI (served via Home Assistant ingress).

Apps documentation: <https://developers.home-assistant.io/docs/apps>
