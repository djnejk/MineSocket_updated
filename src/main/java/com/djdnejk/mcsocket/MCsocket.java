package com.djdnejk.mcsocket;

import com.djdnejk.mcsocket.config.MCsocketConfiguration;
import com.djdnejk.mcsocket.controller.CommandController;
import com.djdnejk.mcsocket.services.MessageService;
import com.djdnejk.mcsocket.services.RecordingService;
import com.djdnejk.mcsocket.services.WebSocketService;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;

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
}
