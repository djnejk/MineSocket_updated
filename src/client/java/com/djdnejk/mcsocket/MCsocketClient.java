package com.djdnejk.mcsocket;

import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.text.Text;

import static com.djdnejk.mcsocket.ModData.MOD_ID;

public class MCsocketClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerEventCallbacks();
    }

    private void registerEventCallbacks() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var player = client.player;
            if (player == null || client.isInSingleplayer()) {
                return;
            }

            player.sendMessage(Text.translatable("callback." + MOD_ID + ".on_singleplayer",
                "MCsocket is available in singleplayer, but you need to activate with the command '/ms'"), false);

            PermissionCheckEvent.EVENT.register((source, permission) -> {
                if (permission.startsWith("command." + MOD_ID + ".ms")) {
                    return TriState.TRUE;
                }
                return TriState.DEFAULT;
            });
        });
    }
}
