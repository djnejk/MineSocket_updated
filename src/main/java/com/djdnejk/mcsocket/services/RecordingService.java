package com.djdnejk.mcsocket.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.djdnejk.mcsocket.ModData;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.*;

import static com.djdnejk.mcsocket.ModData.logger;

public class RecordingService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ACTION_LIST_TYPE = new TypeToken<List<RecordedAction>>() {}.getType();

    private final Map<UUID, ActiveRecording> activeRecordings = new HashMap<>();
    private final Map<UUID, PlaybackState> playbacks = new HashMap<>();
    private final Path recordingsDir;

    private MinecraftServer server;
    private WebSocketService wsService;

    public RecordingService(Path configDir) {
        this.recordingsDir = configDir.resolve("recordings");
    }

    public void setWebSocketService(WebSocketService wsService) {
        this.wsService = wsService;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        try {
            Files.createDirectories(recordingsDir);
        } catch (IOException e) {
            logger.error("Unable to create recordings directory: {}", e.getMessage());
        }

        // Movement tracking per tick
        ServerTickEvents.END_SERVER_TICK.register(srv -> tick(srv.getTicks()));

        // Block break events
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
                recordBlockBreak(serverPlayer, serverWorld, pos, state.getBlock().getName().getString());
            }
        });

    }

    public boolean beginRecording(ServerPlayerEntity player) {
        if (server == null) {
            logger.error("Cannot start recording: server is not available");
            return false;
        }

        UUID id = player.getUuid();
        if (activeRecordings.containsKey(id)) {
            logger.warn("Recording already active for player {}", player.getName().getString());
            return false;
        }

        ActiveRecording recording = new ActiveRecording(player, server.getTicks(), server);
        activeRecordings.put(id, recording);
        sendStatus("recording-started", player.getName().getString(), null);
        return true;
    }

    public boolean saveRecording(ServerPlayerEntity player, String name) {
        UUID id = player.getUuid();
        ActiveRecording recording = activeRecordings.remove(id);
        if (recording == null) {
            logger.warn("No recording to save for {}", player.getName().getString());
            return false;
        }

        Path file = recordingsDir.resolve(sanitizeName(name) + ".json");
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(recording.actions, ACTION_LIST_TYPE, writer);
            logger.info("Saved recording '{}' with {} actions", name, recording.actions.size());
            sendStatus("recording-saved", player.getName().getString(), name);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save recording '{}': {}", name, e.getMessage());
            return false;
        }
    }

    public boolean cancelRecording(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        ActiveRecording removed = activeRecordings.remove(id);
        if (removed != null) {
            sendStatus("recording-cancelled", player.getName().getString(), null);
        }
        return removed != null;
    }

    public boolean playRecording(ServerPlayerEntity player, String name) {
        if (server == null) {
            logger.error("Cannot play recording: server is not available");
            return false;
        }

        if (playbacks.containsKey(player.getUuid())) {
            logger.warn("Playback already active for {}", player.getName().getString());
            return false;
        }

        List<RecordedAction> actions = loadRecording(name);
        if (actions.isEmpty()) {
            logger.warn("Recording '{}' not found or empty", name);
            return false;
        }

        playbacks.put(player.getUuid(), new PlaybackState(server.getTicks(), actions));
        sendStatus("playback-started", player.getName().getString(), name);
        return true;
    }

    public List<String> listRecordings() {
        try {
            return Files.list(recordingsDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .sorted()
                .toList();
        } catch (IOException e) {
            logger.error("Failed to list recordings: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean deleteRecording(String name) {
        Path file = recordingsDir.resolve(sanitizeName(name) + ".json");
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                sendStatus("recording-deleted", null, name);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Failed to delete recording '{}': {}", name, e.getMessage());
            return false;
        }
    }

    public boolean hasActiveRecording(ServerPlayerEntity player) {
        return activeRecordings.containsKey(player.getUuid());
    }

    private void tick(long serverTicks) {
        for (ActiveRecording recording : activeRecordings.values()) {
            recording.captureMovement(serverTicks);
        }

        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, PlaybackState> entry : playbacks.entrySet()) {
            UUID playerId = entry.getKey();
            PlaybackState playback = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                completed.add(playerId);
                continue;
            }

            if (playback.advance(serverTicks, player, server)) {
                completed.add(playerId);
            }
        }

        for (UUID finished : completed) {
            playbacks.remove(finished);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(finished);
            String playerName = player != null ? player.getName().getString() : "unknown";
            sendStatus("playback-finished", playerName, null);
        }
    }

    private void recordBlockBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, String blockName) {
        ActiveRecording recording = activeRecordings.get(player.getUuid());
        if (recording == null) return;

        recording.actions.add(RecordedAction.blockBreak(world, pos, blockName, server.getTicks() - recording.startTick));
    }

    private List<RecordedAction> loadRecording(String name) {
        Path file = recordingsDir.resolve(sanitizeName(name) + ".json");
        if (!Files.exists(file)) {
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            List<RecordedAction> actions = GSON.fromJson(reader, ACTION_LIST_TYPE);
            return actions != null ? actions : List.of();
        } catch (IOException e) {
            logger.error("Failed to load recording '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    private void sendStatus(String event, String playerName, String recordingName) {
        if (wsService != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "recording-status");
            payload.put("event", event);
            if (playerName != null) payload.put("player", playerName);
            if (recordingName != null) payload.put("recording", recordingName);
            payload.put("timestamp", Instant.now().toString());

            wsService.broadcast(GSON.toJson(payload));
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static class ActiveRecording {
        private final ServerPlayerEntity player;
        private final long startTick;
        private final List<RecordedAction> actions;
        private final MinecraftServer server;
        private Vec3d lastPos;
        private float lastYaw;
        private float lastPitch;

        ActiveRecording(ServerPlayerEntity player, long startTick, MinecraftServer server) {
            this.player = player;
            this.startTick = startTick;
            this.server = server;
            this.actions = new ArrayList<>();
            this.lastPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            this.lastYaw = player.getYaw();
            this.lastPitch = player.getPitch();
        }

        void captureMovement(long serverTicks) {
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            float yaw = player.getYaw();
            float pitch = player.getPitch();

            if (!currentPos.equals(lastPos) || yaw != lastYaw || pitch != lastPitch) {
                actions.add(RecordedAction.movement(server.getOverworld(), currentPos, yaw, pitch, serverTicks - startTick));
                lastPos = currentPos;
                lastYaw = yaw;
                lastPitch = pitch;
            }
        }
    }

    private static class PlaybackState {
        private final long startTick;
        private final List<RecordedAction> queue;
        private int index = 0;

        PlaybackState(long startTick, List<RecordedAction> actions) {
            this.startTick = startTick;
            this.queue = new ArrayList<>(actions);
        }

        boolean advance(long serverTicks, ServerPlayerEntity player, MinecraftServer server) {
            while (index < queue.size()) {
                RecordedAction action = queue.get(index);
                if (serverTicks - startTick < action.offset()) break;

                perform(action, player, server);
                index++;
            }
            return index >= queue.size();
        }

        private void perform(RecordedAction action, ServerPlayerEntity player, MinecraftServer server) {
            switch (action.type()) {
                case MOVE -> applyMovement(action, player, server);
                case BREAK -> applyBreak(action, player, server);
                case PICKUP, DROP -> {
                    // Currently no-op until item transfers are recorded.
                }
            }
        }

        private void applyMovement(RecordedAction action, ServerPlayerEntity player, MinecraftServer server) {
            ServerWorld world = resolveWorld(action.worldId(), server, player);
            if (world == null) return;
            player.teleport(world, action.x(), action.y(), action.z(), Collections.emptySet(), action.yaw(), action.pitch(), true);
        }

        private void applyBreak(RecordedAction action, ServerPlayerEntity player, MinecraftServer server) {
            ServerWorld world = resolveWorld(action.worldId(), server, player);
            if (world == null) return;
            BlockPos pos = BlockPos.ofFloored(action.x(), action.y(), action.z());
            world.breakBlock(pos, true, player);
        }

        private ServerWorld resolveWorld(String worldId, MinecraftServer server, ServerPlayerEntity fallback) {
            if (worldId == null || worldId.isEmpty()) {
                return server.getOverworld();
            }

            Identifier id = Identifier.tryParse(worldId);
            if (id == null) {
                return server.getOverworld();
            }

            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            ServerWorld world = server.getWorld(key);
            return world != null ? world : server.getOverworld();
        }
    }
}

record RecordedAction(RecordedActionType type, double x, double y, double z, float yaw, float pitch, String worldId,
                      String itemId, int itemCount, long offset) {
    static RecordedAction movement(ServerWorld world, Vec3d pos, float yaw, float pitch, long offset) {
        return new RecordedAction(RecordedActionType.MOVE, pos.x, pos.y, pos.z, yaw, pitch,
            world.getRegistryKey().getValue().toString(), null, 0, offset);
    }

    static RecordedAction blockBreak(ServerWorld world, BlockPos pos, String block, long offset) {
        return new RecordedAction(RecordedActionType.BREAK, pos.getX(), pos.getY(), pos.getZ(), 0, 0,
            world.getRegistryKey().getValue().toString(), block, 0, offset);
    }
}

enum RecordedActionType {
    MOVE,
    BREAK,
    PICKUP,
    DROP
}
