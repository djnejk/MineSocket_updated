package com.djdnejk.mcsocket;

import com.djdnejk.mcsocket.config.MCsocketConfiguration;
import com.djdnejk.mcsocket.controller.CommandController;
import com.djdnejk.mcsocket.network.MenuActionPayload;
import com.djdnejk.mcsocket.services.MessageService;
import com.djdnejk.mcsocket.services.RecordingService;
import com.djdnejk.mcsocket.services.WebSocketService;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

import static com.djdnejk.mcsocket.ModData.MOD_ID;
import static com.djdnejk.mcsocket.ModData.logger;

public class MCsocket implements ModInitializer {
    MCsocketConfiguration config;
    WebSocketService wsService;
    MessageService messageService;
    CommandController commandController;
    RecordingService recordingService;

    @Override
    public void onInitialize() {
        logger.info("MCsocket is initializing");

        // Initialize the configuration
        config = new MCsocketConfiguration();

        // Register events callbacks
        registerEventsCallbacks();

        // Initialize services
        recordingService = new RecordingService(config.getConfigPath());
        messageService = new MessageService(recordingService);
        wsService = new WebSocketService(config, messageService);
        messageService.setWebSocketService(wsService);
        recordingService.setWebSocketService(wsService);

        // Register the commands
        commandController = new CommandController(wsService, recordingService);

        registerNetworkPayloads();
    }

    private void registerNetworkPayloads() {
        PayloadTypeRegistry.playC2S().register(MenuActionPayload.ID, MenuActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MenuActionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleMenuAction(context.player(), payload))
        );
    }

    private void registerEventsCallbacks() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();

            if (!server.isDedicated() || Permissions.check(player, "command." + MOD_ID + ".ms", 3)) {
                return;
            }

            player.sendMessage(Text.translatable("callback." + MOD_ID + ".on_op_join",
                "You are using MCsocket, you can configure/use the mod by using the '/ms' command"));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            messageService.start(server, config);
            recordingService.start(server);
            if (!server.isDedicated() || !config.autoStart) {
                return;
            }

            if  (!wsService.tryToStart()) {
                logger.error("Failed to start WebSocket server");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> wsService.tryToStop());

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(((server, resourceManager, success) -> {
            logger.info("Reloading configuration");
            try {
                config.reload();
                if (!wsService.tryToReload()) {
                    logger.error("Failed to reload WebSocket server");
                }
            } catch (Exception e) {
                logger.error("Error reloading configuration: {}", e.getMessage());
            }
        }));

        ServerTickEvents.END_SERVER_TICK.register(server -> messageService.tick());
    }

    private void handleMenuAction(ServerPlayerEntity player, MenuActionPayload payload) {
        if (player == null) {
            return;
        }

        switch (payload.action()) {
            case START_WS -> {
                boolean started = wsService.tryToStart();
                Text feedback = started
                    ? Text.translatable("command.mcsocket.started")
                    : Text.translatable("command.mcsocket.start_error");
                player.sendMessage(ModData.prefixed(feedback));
            }
            case STOP_WS -> {
                boolean stopped = wsService.tryToStop();
                Text feedback = stopped
                    ? Text.translatable("command.mcsocket.stopped")
                    : Text.translatable("command.mcsocket.stop_error");
                player.sendMessage(ModData.prefixed(feedback));
            }
            case START_RECORDING -> {
                if (recordingService.beginRecording(player)) {
                    player.sendMessage(ModData.prefixed(Text.literal("Recording started. Use save to persist.")));
                } else {
                    player.sendMessage(ModData.prefixed(Text.literal("Could not start a new recording.")));
                }
            }
            case SAVE_RECORDING -> {
                String name = payload.recordingName().isBlank() ? "default" : payload.recordingName();
                if (recordingService.saveRecording(player, name)) {
                    player.sendMessage(ModData.prefixed(Text.literal("Recording saved as '" + name + "'.")));
                } else {
                    player.sendMessage(ModData.prefixed(Text.literal("No active recording to save.")));
                }
            }
            case PLAY_RECORDING -> {
                String name = payload.recordingName().isBlank() ? "default" : payload.recordingName();
                if (recordingService.playRecording(player, name)) {
                    player.sendMessage(ModData.prefixed(Text.literal("Playing recording '" + name + "'.")));
                } else {
                    player.sendMessage(ModData.prefixed(Text.literal("Could not play recording '" + name + "'.")));
                }
            }
            case LIST_RECORDINGS -> {
                var names = recordingService.listRecordings();
                if (names.isEmpty()) {
                    player.sendMessage(ModData.prefixed(Text.literal("No recordings saved.")));
                } else {
                    player.sendMessage(ModData.prefixed(Text.literal("Recordings: " + String.join(", ", names))));
                }
            }
            case CANCEL_RECORDING -> {
                if (recordingService.cancelRecording(player)) {
                    player.sendMessage(ModData.prefixed(Text.literal("Recording cancelled.")));
                } else {
                    player.sendMessage(ModData.prefixed(Text.literal("No active recording.")));
                }
            }
        }
    }
}
