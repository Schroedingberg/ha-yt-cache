# YouTube to Media Home Assistant App

Downloads YouTube media into Home Assistant's persistent `/share/youtube-to-media` directory.

See the [app documentation](./youtube-to-media/DOCS.md) for installation and access details.

## Development

Run the VS Code task **Development: Start Local Server** to execute the working tree directly on port `8098`. Calva Jack-in uses the Babashka project in `youtube-to-media/`.

## Production

`youtube-to-media/config.yaml` points to `ghcr.io/schroedingberg/youtube-to-media`. Use the **Production** tasks to install, start, or update that published image on port `8099`.

Pull requests build without publishing. Pushes to `main` publish versioned and `latest` images to GHCR. After the first publish, make the GHCR package public so Home Assistant can pull it.

Apps documentation: <https://developers.home-assistant.io/docs/apps>
