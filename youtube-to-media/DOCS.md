# Home Assistant App: YouTube to Media

The production app is served through the Home Assistant frontend via ingress, so access is authenticated with your Home Assistant login. Open it from the app page in Home Assistant. The local development server task runs the working tree on port `8098`.

Downloads are stored persistently in `/share/youtube-to-media`.

## Development

The Babashka project is in this app directory. Use Calva's Babashka Jack-in for a local REPL. The app image does not expose an nREPL port.

AppArmor remains disabled while the app's yt-dlp subprocess and network access are validated.
