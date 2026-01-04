package com.djdnejk.mcsocket;

import com.djdnejk.mcsocket.client.ui.MCsocketConfigScreen;
import com.djdnejk.mcsocket.network.MenuActionPayload;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import static com.djdnejk.mcsocket.ModData.MOD_ID;

public class MCsocketClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerPayloads();
        registerEventCallbacks();
        registerMenuButton();
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MenuActionPayload.ID, MenuActionPayload.CODEC);
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

    private void registerMenuButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen) && !(screen instanceof OptionsScreen)) {
                return;
            }

            var button = Screens.getButtons(screen).stream()
                .filter(widget -> widget.getWidth() == 200)
                .findFirst()
                .map(existing -> existing.getY() + existing.getHeight() + 4)
                .orElseGet(() -> screen.height / 4 + 132);

            Screens.getButtons(screen).add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                ModData.brandText(),
                b -> client.setScreen(new MCsocketConfigScreen(screen))
            ).position(screen.width / 2 - 100, button).size(200, 20).build());
        });
    }
}
