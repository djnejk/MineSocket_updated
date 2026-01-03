package org.zamecki.minesocket.services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.zamecki.minesocket.config.MineSocketConfiguration;
import org.zamecki.minesocket.event.EventManager;

import static org.zamecki.minesocket.ModData.logger;

public class MessageService {
    MinecraftServer server;
    EventManager eventManager;

    public void start(MinecraftServer server, MineSocketConfiguration config) {
        if (server == null) {
            logger.error("Server is not set");
            return;
        }
        this.server = server;
        eventManager = new EventManager(server, config);
    }

    public void handleMessage(String message) {
        if (server == null) {
            logger.error("Server is not set");
            return;
        }

        String trimmedMessage = message.trim();
        if (trimmedMessage.isEmpty()) {
            logger.warn("Received empty message");
            return;
        }

        String[] words = trimmedMessage.split(" ", 2);
        String command = words[0];
        String args = words.length == 2 ? words[1] : "";

        if (command.equalsIgnoreCase("command")) {
            handleServerCommand(args);
        } else if (command.equalsIgnoreCase("event")) {
            handleGameEvent(args);
        } else if (command.equalsIgnoreCase("control")) {
            handleControlCommand(args);
        } else {
            logger.error("Unknown message type: '{}'", command);
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
}
