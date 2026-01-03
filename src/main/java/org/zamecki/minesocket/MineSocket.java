package org.zamecki.minesocket;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import org.zamecki.minesocket.config.MineSocketConfiguration;
import org.zamecki.minesocket.controller.CommandController;
import org.zamecki.minesocket.services.MessageService;
import org.zamecki.minesocket.services.RecordingService;
import org.zamecki.minesocket.services.WebSocketService;

import static org.zamecki.minesocket.ModData.MOD_ID;
import static org.zamecki.minesocket.ModData.logger;

public class MineSocket implements ModInitializer {
    MineSocketConfiguration config;
    WebSocketService wsService;
    MessageService messageService;
    CommandController commandController;
    RecordingService recordingService;

    @Override
    public void onInitialize() {
        logger.info("MineSocket is initializing");

        // Initialize the configuration
        config = new MineSocketConfiguration();

        // Register events callbacks
        registerEventsCallbacks();

        // Initialize services
        recordingService = new RecordingService(config.getConfigPath());
        messageService = new MessageService(recordingService);
        wsService = new WebSocketService(config, messageService);
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
                "You are using MineSocket, you can configure/use the mod by using the '/ms' command"));
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
