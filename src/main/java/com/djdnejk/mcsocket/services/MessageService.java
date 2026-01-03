package com.djdnejk.mcsocket.services;

import com.djdnejk.mcsocket.config.MCsocketConfiguration;
import com.djdnejk.mcsocket.event.EventManager;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.java_websocket.WebSocket;

import static com.djdnejk.mcsocket.ModData.logger;

public class MessageService {
    private static final Gson GSON = new Gson();

    MinecraftServer server;
    EventManager eventManager;
    private final RecordingService recordingService;
    private WebSocketService wsService;

    public MessageService(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    public void setWebSocketService(WebSocketService wsService) {
        this.wsService = wsService;
    }

    public void start(MinecraftServer server, MCsocketConfiguration config) {
        if (server == null) {
            logger.error("Server is not set");
            return;
        }
        this.server = server;
        eventManager = new EventManager(server, config);
    }

    public void handleMessage(WebSocket conn, String message) {
        if (server == null) {
            logger.error("Server is not set");
            return;
        }

        String trimmedMessage = message.trim();
        if (trimmedMessage.isEmpty()) {
            logger.warn("Received empty message");
            return;
        }

        InboundMessage inbound = parseJson(trimmedMessage);
        if (inbound != null && inbound.type() != null) {
            routeInbound(conn, inbound);
            return;
        }

        String[] words = trimmedMessage.split(" ", 2);
        String command = words[0];
        String args = words.length == 2 ? words[1] : "";

        if (command.equalsIgnoreCase("command")) {
            handleServerCommand(args);
            sendCallback(conn, null, "accepted", "Command dispatched");
        } else if (command.equalsIgnoreCase("event")) {
            handleGameEvent(args);
        } else if (command.equalsIgnoreCase("control")) {
            handleControlCommand(args);
        } else if (command.equalsIgnoreCase("recording")) {
            handleRecordingMessage(args);
        } else {
            logger.error("Unknown message type: '{}'", command);
            sendCallback(conn, null, "error", "Unknown message type: " + command);
        }
    }

    public void tick() {
        eventManager.onServerTick();
    }

    private void handleServerCommand(String args) {
        if (args.isEmpty()) {
            logger.error("No command provided");
            return;
        }

        logger.info("Executing command: '{}'", args);
        server.execute(() -> {
            var source = server.getCommandSource().withLevel(4);
            var parse = server.getCommandManager().getDispatcher().parse(args, source);
            server.getCommandManager().execute(parse, args);
        });
    }

    private void handleGameEvent(String args) {
        if (args.isEmpty()) {
            logger.error("No event name provided");
            return;
        }

        String[] eventArgs = args.split(" ");
        String eventName = eventArgs[0];
        String[] eventParams = new String[eventArgs.length - 1];
        System.arraycopy(eventArgs, 1, eventParams, 0, eventArgs.length - 1);

        server.execute(() -> {
            if (!eventManager.handleEvent(eventName, eventParams)) {
                logger.error("Event '{}' not found", eventName);
            }
        });
    }

    private void handleControlCommand(String args) {
        if (args.isEmpty()) {
            logger.error("No control action provided");
            return;
        }

        String[] controlArgs = args.split(" ");
        String action = controlArgs[0].toLowerCase();

        switch (action) {
            case "move" -> handleMove(controlArgs);
            case "break" -> handleBreak(controlArgs);
            default -> logger.error("Unknown control action: '{}'", action);
        }
    }

    private void handleMove(String[] args) {
        if (args.length < 5) {
            logger.error("Usage: control move <player> <xVelocity> <yVelocity> <zVelocity>");
            return;
        }

        String playerName = args[1];

        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);

            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
                if (player == null) {
                    logger.error("Player '{}' not found for move action", playerName);
                    return;
                }

                player.addVelocity(x, y, z);
                player.velocityModified = true;
                logger.info("Applied velocity ({}, {}, {}) to player '{}'", x, y, z, playerName);
            });
        } catch (NumberFormatException e) {
            logger.error("Invalid velocity values for move action");
        }
    }

    private void handleBreak(String[] args) {
        if (args.length < 5) {
            logger.error("Usage: control break <player> <x> <y> <z>");
            return;
        }

        String playerName = args[1];

        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            BlockPos targetPos = new BlockPos(x, y, z);

            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
                if (player == null) {
                    logger.error("Player '{}' not found for break action", playerName);
                    return;
                }

                ServerWorld world = (ServerWorld) player.getEntityWorld();
                boolean broken = world.breakBlock(targetPos, true, player);
                if (broken) {
                    logger.info("Player '{}' broke block at {}", playerName, targetPos.toShortString());
                } else {
                    logger.warn("Failed to break block at {} for player '{}'", targetPos.toShortString(), playerName);
                }
            });
        } catch (NumberFormatException e) {
            logger.error("Invalid block coordinates for break action");
        }
    }

    private void handleRecordingMessage(String args) {
        if (args.isEmpty()) {
            logger.error("No recording action provided");
            return;
        }

        String[] parts = args.split(" ");
        if (parts.length < 2) {
            logger.error("Usage: recording <action> <player> [name]");
            return;
        }

        String action = parts[0].toLowerCase();
        String playerName = parts[1];
        String recordingName = parts.length >= 3 ? parts[2] : "";

        switch (action) {
            case "play" -> server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
                if (player == null) {
                    logger.error("Player '{}' not found for playback", playerName);
                    return;
                }
                if (!recordingService.playRecording(player, recordingName)) {
                    logger.warn("Playback could not start for '{}'", recordingName);
                }
            });
            default -> logger.error("Unsupported recording action: '{}'", action);
        }
    }

    private InboundMessage parseJson(String message) {
        try {
            return GSON.fromJson(message, InboundMessage.class);
        } catch (JsonSyntaxException ignored) {
            return null;
        }
    }

    private void routeInbound(WebSocket conn, InboundMessage inbound) {
        String callbackId = inbound.callbackId();
        String type = inbound.type().toLowerCase();

        switch (type) {
            case "command" -> {
                handleServerCommand(defaultString(inbound.payload()));
                sendCallback(conn, callbackId, "accepted", "Command dispatched");
            }
            case "event" -> {
                handleGameEvent(defaultString(inbound.payload()));
                sendCallback(conn, callbackId, "accepted", "Event dispatched");
            }
            case "control" -> {
                handleControlCommand(defaultString(inbound.payload()));
                sendCallback(conn, callbackId, "accepted", "Control dispatched");
            }
            case "recording" -> {
                String args = String.join(" ",
                    defaultString(inbound.action()),
                    defaultString(inbound.player()),
                    defaultString(inbound.recording())
                ).trim();
                handleRecordingMessage(args);
                sendCallback(conn, callbackId, "accepted", "Recording command dispatched");
            }
            default -> {
                logger.error("Unknown JSON message type: '{}'", inbound.type());
                sendCallback(conn, callbackId, "error", "Unknown JSON message type: " + inbound.type());
            }
        }
    }

    private void sendCallback(WebSocket conn, String callbackId, String status, String detail) {
        if (conn == null || callbackId == null || callbackId.isBlank() || wsService == null) {
            return;
        }

        var payload = new CallbackPayload(callbackId, status, detail);
        conn.send(GSON.toJson(payload));
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record InboundMessage(String type, String payload, String action, String player, String recording, String callbackId) { }

    private record CallbackPayload(String callbackId, String status, String detail) { }
}
