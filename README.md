<div style="text-align: center; align-items: center; align-content: center;">
<img src="src/main/resources/assets/minesocket/icon.png" width="128" height="128" alt="MineSocket Logo">
</div>

# MineSocket

MineSocket is a Minecraft Fabric mod that allows Minecraft to communicate with external applications
via a WebSocket connection. It enables sending commands and events **to Minecraft** and receiving
events **from Minecraft** in real time.

> ⚠️ This project is a **fork** of the original MineSocket mod and has been **updated to support
Minecraft 1.21.10**.

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
- [ ] Secure WebSocket connections (authentication / encryption)
- [ ] Handle callbacks and events sent back to clients

---

## Background

This Minecraft mod is intended to be a Fabric-based replacement for the Bukkit plugin  
[**Minecraft WebSocket Integration**](https://github.com/KK964/Minecraft_Websocket_Intergration/),
which is no longer actively maintained.

Because of this, MineSocket is compatible with existing configurations used by
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
2. Drop the MineSocket JAR into your `mods` folder.
3. Launch the game/server. The WebSocket server will auto-start using the values in `config/minesocket.toml` unless `auto_start` is set to `false`.
4. Use the in-game commands to manage the WebSocket server:
   - `/ms help` – Show built-in help
   - `/ms start` – Start the WebSocket server
   - `/ms stop` – Stop the WebSocket server

Configuration values:

| Key | Default | Description |
| --- | --- | --- |
| `host` | `localhost` | Interface the WebSocket server binds to |
| `port` | `8887` | Listening port |
| `auto_start` | `true` | Automatically start on server launch |
| `event_boss_bar` | `false` | Show a boss bar for running events |

---

## WebSocket API

Connect a client (Node.js, Python, Streamer.bot, etc.) to `ws://<host>:<port>` using the configured host/port.

### Messages -> Minecraft

Messages are plain text. The first word chooses the handler; everything after it is parsed as arguments.

#### Run Minecraft commands
```
command <minecraft command>
```
*Example:* `command time set day`

#### Trigger MineSocket events
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

### Messages <- Minecraft

Callbacks for connection lifecycle events are emitted as localized chat messages. Additional callbacks (e.g., authentication, custom events) are not yet implemented.

---
