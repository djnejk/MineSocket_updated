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
