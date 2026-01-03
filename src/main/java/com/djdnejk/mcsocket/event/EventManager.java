package com.djdnejk.mcsocket.event;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import com.djdnejk.mcsocket.config.MCsocketConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class EventManager {
    private final Map<String, IGameEvent> events = new HashMap<>();
    private final List<IGameEvent> runningEvents = new ArrayList<>();
    private final Map<IGameEvent, ServerBossBar> eventBossBars = new HashMap<>();
    private final MinecraftServer server;
    private final MCsocketConfiguration config;

    public EventManager(MinecraftServer server, MCsocketConfiguration config) {
        this.server = server;
        this.config = config;
        registerDefaultEvents();
    }

    private void registerDefaultEvents() {
        registerEvent(new FireworkEvent(server));
    }

    public void registerEvent(IGameEvent event) {
        events.put(event.getName().toLowerCase(), event);
    }

    public boolean handleEvent(String eventName, String[] args) {
        IGameEvent event = events.get(eventName.toLowerCase());
        if (event == null) return false;
        if (!event.start(args)) return false;

        // Create boss bar when event starts if enabled in config
        if (config.eventBossBar) {
            ServerBossBar bossBar = new ServerBossBar(
                event.getDisplayName(),
                event.getBossBarColor(),
                event.getBossBarStyle()
            );

            // Add all players to the boss bar
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                bossBar.addPlayer(player);
            }

            // Store the boss bar
            eventBossBars.put(event, bossBar);
        }

        // Add the event to the running events list
        runningEvents.add(event);
        return true;
    }

    public void onServerTick() {
        List<IGameEvent> completedEvents = new ArrayList<>();

        for (IGameEvent event : runningEvents) {
            // Update boss bar progress if boss bars are enabled
            if (config.eventBossBar) {
                ServerBossBar bossBar = eventBossBars.get(event);
                if (bossBar != null) {
                    bossBar.setPercent(event.getProgress());
                }
            }

            // Check if event has finished
            if (event.tick()) {
                completedEvents.add(event);
            }
        }

        // Remove completed events and their boss bars
        for (IGameEvent completedEvent : completedEvents) {
            runningEvents.remove(completedEvent);

            if (config.eventBossBar) {
                ServerBossBar bossBar = eventBossBars.remove(completedEvent);
                if (bossBar != null) {
                    bossBar.clearPlayers();
                }
            }
        }
    }
}
