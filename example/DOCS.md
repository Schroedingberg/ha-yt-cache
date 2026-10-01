# Home Assistant App: Example app

## How to use

The app runs a Babashka nREPL server on TCP port `1667`.

Connect your editor's nREPL client to `localhost:1667` from the development
environment. In Calva, use **Connect to a running REPL server** and enter
`localhost:1667`.

The nREPL server allows connected clients to execute code in the app
container. Keep this development app on a trusted network and do not expose
port `1667` to the internet. AppArmor is disabled because the template's s6
service setup cannot start under its current profile, so this app is for local
development only.
