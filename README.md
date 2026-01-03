<div style="text-align: center; align-items: center; align-content: center;">
<img src="src/main/resources/assets/mcsocket/icon.png" width="128" height="128" alt="MCsocket Logo">
</div>

# MCsocket

MCsocket is a Minecraft Fabric mod that allows Minecraft to communicate with external applications
via a WebSocket connection. It enables sending commands and events **to Minecraft** and receiving
events **from Minecraft** in real time.

> ⚠️ MCsocket is derived from the original MineSocket mod but adds authentication/TLS support,
> recording playback controls, richer status callbacks, and updated compatibility for **Minecraft 1.21.10**.

This mod is still in development and may change at any time.

---

## Minecraft Version

- ✅ **Minecraft 1.21.10**
- Fabric Loader + Fabric API

---

## Features

- [x] Handle WebSocket connections and disconnections
- [x] Receive messages from a WebSocket client (e.g. Streamer.bot, custom bots, Node.js, Python)
- [x] Execute Minecraft commands via WebSocket
- [x] Apply movement impulses to players via WebSocket control messages
- [x] Break blocks on behalf of a player via WebSocket control messages
- [x] Record and replay player actions with in-game commands and WebSocket triggers
- [x] Secure WebSocket connections (authentication / encryption)
- [x] Handle callbacks and events sent back to clients
- [x] Client- and server-side compatibility so you can run MCsocket on player-hosted servers
- [x] Client-side settings screen (pause menu -> Options -> MCsocket) to edit host/port/auth/TLS without touching files

---

## Background

This Minecraft mod is intended to be a Fabric-based replacement for the Bukkit plugin  
[**Minecraft WebSocket Integration**](https://github.com/KK964/Minecraft_Websocket_Intergration/),
which is no longer actively maintained.

Because of this, MCsocket is compatible with existing configurations used by
[**Streamer.bot**](https://streamer.bot/) via the following extension:

👉 https://extensions.streamer.bot/t/minecraft-websocket-integration/167

---

## Fork Information

This repository is a fork of the original MineSocket project with:
- updated dependencies
- fixes for modern Fabric versions
- compatibility with **Minecraft 1.21.10**

Original project credit belongs to the original author.

---

## Getting Started

1. Install Fabric Loader and Fabric API for **Minecraft 1.21.10**.
2. Drop the MCsocket JAR into your `mods` folder (works on both dedicated and client-hosted servers).
3. Launch the game/server. The WebSocket server will auto-start using the values in `config/mcsocket.toml` unless `auto_start` is set to `false`.
4. Use the in-game commands to manage the WebSocket server:
   - `/ms help` – Show built-in help
   - `/ms start` – Start the WebSocket server
   - `/ms stop` – Stop the WebSocket server

Configuration values:

| Key | Default | Description |
| --- | --- | --- |
| `host` | `0.0.0.0` | Interface the WebSocket server binds to |
| `port` | `8887` | Listening port |
| `auto_start` | `true` | Automatically start on server launch |
| `event_boss_bar` | `false` | Show a boss bar for running events |
| `require_auth_token` | `false` | Require clients to supply a token before connecting |
| `auth_token` | `change-me` | The token value clients must send in the `Authorization` header or `?token=` query |
| `ssl_enabled` | `false` | Enable TLS for secure `wss://` connections |
| `ssl_keystore_path` | `config/mcsocket/keystore.p12` | Path to the PKCS#12 or JKS keystore containing the certificate |
| `ssl_keystore_password` | `changeit` | Password for the keystore and its key |

### Adjust settings from the client

- Open the pause menu → **Options** → **MCsocket** to edit the same values (host, port, auth token, TLS paths, auto-start, boss bar).
- Changes are written back to `config/mcsocket.toml` so you can tweak singleplayer/LAN servers without leaving the game.

---

## WebSocket API

## Recording Commands

- `/ms recording record` – start capturing the executing player’s actions
- `/ms recording save <name>` – write the capture to `config/mcsocket/recordings/<name>.json`
- `/ms recording play <name>` – replay the saved capture for the executing player
- `/ms recording list` – list saved recordings
- `/ms recording delete <name>` – delete a saved recording
- `/ms recording cancel` – discard the current unsaved recording

Use `/ms recording record` before moving, mining, or interacting. The recorder logs per-tick position and look direction so playback teleports the player along the exact path and replays block breaks in the correct world.

Connect a client (Node.js, Python, Streamer.bot, etc.) to `ws://<host>:<port>` using the configured host/port.
- If `require_auth_token` is enabled, send `Authorization: Bearer <auth_token>` **or** append `?token=<auth_token>` to the WebSocket URL.
- If `ssl_enabled` is enabled and the keystore is valid, connect via `wss://<host>:<port>`.

### Messages -> Minecraft

Messages are plain text by default. The first word chooses the handler; everything after it is parsed as arguments.

You can also send JSON to request callback responses:
```json
{
  "type": "command" | "event" | "control" | "recording",
  "payload": "time set day",              // required for command/event/control
  "action": "play",                        // recording action
  "player": "Steve",                      // recording target
  "recording": "demo",                    // recording name
  "callbackId": "any-identifier"          // echoed in the response below
}
```

#### Run Minecraft commands
```
command <minecraft command>
```
*Example:* `command time set day`

#### Trigger MCsocket events
```
event <eventName> [args...]
```
*Built-in example:* `event FireworkEvent PlayerName 60 5 10.0 {"text":"Fireworks for Player"}`

#### Control player movement
Applies a velocity impulse to a player.
```
control move <playerName> <xVelocity> <yVelocity> <zVelocity>
```
*Example:* `control move Steve 0 0.7 1.2`

#### Break blocks on behalf of a player
Breaks the specified block position in the player’s world and drops loot.
```
control break <playerName> <x> <y> <z>
```
*Example:* `control break Alex 123 64 -45`

#### Trigger playback from outside the game
Start playback of a saved recording for a target player.
```
recording play <playerName> <recordingName>
```
The server broadcasts JSON status updates for recording and playback to every connected WebSocket client:
```
{
  "type": "recording-status",
  "event": "recording-started | recording-saved | recording-cancelled | playback-started | playback-finished | recording-deleted",
  "player": "<playerName>",
  "recording": "<recordingName>",
  "timestamp": "<ISO 8601 time>"
}
```

### Messages <- Minecraft

- Recording and playback statuses are broadcast to all connected clients (see JSON above).
- When you supply a `callbackId` in an inbound JSON message, MCsocket replies to that connection with:
```json
{
  "callbackId": "<same id you sent>",
  "status": "accepted | error",
  "detail": "Short description of what was dispatched"
}
```
- Connection lifecycle feedback still appears in chat using the localized translations bundled with the mod.

---
