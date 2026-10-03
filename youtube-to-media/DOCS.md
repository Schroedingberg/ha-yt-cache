# Home Assistant App: YouTube to Media

The production app is served through the Home Assistant frontend via ingress, so access is authenticated with your Home Assistant login. Open it from the app page in Home Assistant. The local development server task runs the working tree on port `8098`.

Downloads are stored persistently in `/share/youtube-to-media`.

## Development

Fastest full-stack iteration is via Babashka tasks, which run the app in Docker directly (bypassing the Supervisor) on `http://localhost:8099`:

- `bb dev run` — build and run the app (downloads + PO token provider included).
- `bb dev logs` — follow logs.
- `bb dev stop` — stop the container.

The app runs on the JVM (`clojure -M -m youtube-to-media.core`). For a quick REPL or HTTP-only iteration without the provider, use Calva's Babashka Jack-in in `youtube-to-media/`. The app image does not expose an nREPL port.

AppArmor remains disabled while the app's yt-dlp subprocess and network access are validated.
