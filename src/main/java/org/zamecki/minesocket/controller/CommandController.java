package org.zamecki.minesocket.controller;

import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.zamecki.minesocket.services.RecordingService;
import org.zamecki.minesocket.services.WebSocketService;

import static org.zamecki.minesocket.ModData.MOD_ID;

public class CommandController {

    public CommandController(WebSocketService wsService, RecordingService recordingService) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("ms")
                // Uses the LuckPerms permission system to check if the player has the permission to use the command
                .requires(source -> Permissions.check(source, "command." + MOD_ID + ".ms", 3))
                .executes(this::sendHelp)
                .then(CommandManager.literal("help").executes(this::sendHelp))
                .then(CommandManager.literal("start").executes(ctx -> startWebSocket(ctx, wsService)))
                .then(CommandManager.literal("stop").executes(ctx -> stopWebSocket(ctx, wsService)))
                .then(CommandManager.literal("recording")
                    .then(CommandManager.literal("record").executes(ctx -> startRecording(ctx, recordingService)))
                    .then(CommandManager.literal("save").then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> saveRecording(ctx, recordingService))))
                    .then(CommandManager.literal("play").then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> playRecording(ctx, recordingService))))
                    .then(CommandManager.literal("list").executes(ctx -> listRecordings(ctx, recordingService)))
                    .then(CommandManager.literal("delete").then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> deleteRecording(ctx, recordingService))))
                    .then(CommandManager.literal("cancel").executes(ctx -> cancelRecording(ctx, recordingService))))));
    }

    private int sendHelp(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.translatableWithFallback(
            "command." + MOD_ID + ".help",
            """
                MineSocket Help:
                /ms - Main command
                /ms help - Show this help message
                /ms start - Start the WebSocket server
                /ms stop - Stop the WebSocket server"""), false);
        return 1;
    }

    private int startWebSocket(CommandContext<ServerCommandSource> ctx, WebSocketService wsService) {
        if (!wsService.isRunning()) {
            if (!wsService.tryToStart()) {
                ctx.getSource().sendFeedback(() -> Text.translatableWithFallback(
                    "command." + MOD_ID + ".start_error",
                    "An error occurred while starting the WebSocket server"), false);
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.translatableWithFallback(
                "command." + MOD_ID + ".started",
                "WebSocket server started"), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.translatableWithFallback(
                "command." + MOD_ID + ".already_running",
                "WebSocket server is already running"), false);
        }
        return 1;
    }

    private int stopWebSocket(CommandContext<ServerCommandSource> ctx, WebSocketService wsService) {
        if (wsService.isRunning()) {
            if (!wsService.tryToStop()) {
                ctx.getSource().sendFeedback(() -> Text.translatableWithFallback("command." + MOD_ID + ".stop_error",
                    "An error occurred while stopping the WebSocket server"), false);
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.translatableWithFallback("command." + MOD_ID + ".stopped",
                "WebSocket server stopped"), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.translatableWithFallback("command." + MOD_ID + ".not_running",
                "WebSocket server is not running"), false);
        }
        return 1;
    }

    private int startRecording(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Recording can only be started by a player."), false);
            return 0;
        }

        if (recordingService.beginRecording(player)) {
            ctx.getSource().sendFeedback(() -> Text.literal("Recording started. Use /ms recording save <name> to persist."), false);
            return 1;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("Recording already running or could not start."), false);
        return 0;
    }

    private int saveRecording(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Only players can save their recording."), false);
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        if (recordingService.saveRecording(player, name)) {
            ctx.getSource().sendFeedback(() -> Text.literal("Recording saved as '" + name + "'."), false);
            return 1;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("No active recording to save."), false);
        return 0;
    }

    private int playRecording(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Only players can play recordings."), false);
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        if (recordingService.playRecording(player, name)) {
            ctx.getSource().sendFeedback(() -> Text.literal("Playing recording '" + name + "'."), false);
            return 1;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("Recording '" + name + "' not found or already playing."), false);
        return 0;
    }

    private int listRecordings(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        var names = recordingService.listRecordings();
        if (names.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No recordings saved."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Recordings: " + String.join(", ", names)), false);
        return 1;
    }

    private int deleteRecording(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        String name = StringArgumentType.getString(ctx, "name");
        if (recordingService.deleteRecording(name)) {
            ctx.getSource().sendFeedback(() -> Text.literal("Deleted recording '" + name + "'."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Recording '" + name + "' not found."), false);
        return 0;
    }

    private int cancelRecording(CommandContext<ServerCommandSource> ctx, RecordingService recordingService) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Only players can cancel recordings."), false);
            return 0;
        }

        if (recordingService.cancelRecording(player)) {
            ctx.getSource().sendFeedback(() -> Text.literal("Recording cancelled."), false);
            return 1;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("No active recording."), false);
        return 0;
    }
}
