package org.zamecki.minesocket.event;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.TextCodecs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.zamecki.minesocket.ModData.MOD_ID;
import static org.zamecki.minesocket.ModData.logger;

/// FireworkEvent: Creates fireworks around a player in random positions.
///
/// Usage via WebSocket:
/// "event FireworkEvent \[playerName] \[duration] \[interval] \[radius]"
///
///
/// Examples:
/// - "event FireworkEvent Player1"
///   Creates fireworks around the player
///
///
/// - "event FireworkEvent Player1 60 5"
///   Creates fireworks every 5 ticks for 60 ticks
///
///
/// - "event FireworkEvent Player1 40 2 10.0"
///   Creates fireworks every 2 ticks for 40 ticks within a radius of 10.0
///
/// - "event FireworkEvent Player1 60 5 10.0 Custom boss bar name"
///   Creates fireworks with a custom boss bar name
///
/// - "event FireworkEvent Player1 60 5 10.0 {"text":"Colorful Boss Bar","color":"gold","bold":true}"
///   Creates fireworks with a custom boss bar name using Raw JSON text format
public class FireworkEvent implements IGameEvent {
    private static final int DEFAULT_DURATION = 1;
    private static final int DEFAULT_INTERVAL = 2;
    private static final double DEFAULT_RADIUS = 5.0;

    private final MinecraftServer server;
    private final Random random = new Random();

    private int ticksRemaining;
    private int initialDuration;
    private int spawnInterval;
    private int ticksSinceLastSpawn;
    private String playerName;
    private double radius;
    private Text bossBarName;

    public FireworkEvent(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String getName() {
        return "FireworkEvent";
    }

    @Override
    public boolean start(String[] args) {
        if (args.length < 1) {
            logger.error("FireworkEvent: Player name not provided");
            return false;
        }

        this.playerName = args[0];
        this.initialDuration = getArg(args, 1, DEFAULT_DURATION, "duration", Integer::parseInt);
        this.initialDuration = Math.max(1, this.initialDuration);
        this.ticksRemaining = this.initialDuration;
        this.spawnInterval = getArg(args, 2, DEFAULT_INTERVAL, "interval", Integer::parseInt);
        this.radius = getArg(args, 3, DEFAULT_RADIUS, "radius", Double::parseDouble);
        this.ticksSinceLastSpawn = 0;

        ServerPlayerEntity player = findPlayer(this.playerName);
        if (player == null)
            return false;

        this.bossBarName = null;

        if (args.length > 4) {
            String textArg = Arrays.stream(args).skip(4).reduce((a, b) -> a + " " + b).orElse("");

            // Try to parse as Raw JSON text format
            try {
                this.bossBarName = net.minecraft.text.TextCodecs.CODEC
                        .parse(player.getRegistryManager().getOps(com.mojang.serialization.JsonOps.INSTANCE),
                                com.google.gson.JsonParser.parseString(textArg))
                        .result()
                        .orElse(net.minecraft.text.Text.of(textArg));
            } catch (Exception e) {
                // If not valid JSON, use as plain text
                logger.warn("Failed to parse boss bar text as JSON, using as plain text: {}", textArg);
                this.bossBarName = Text.of(textArg);
            }
        }

        logger.info("FireworkEvent started for '{}' with duration of {} ticks and interval of {} ticks, radius {}",
                this.playerName, this.ticksRemaining, this.spawnInterval, this.radius);

        return true;
    }

    @Override
    public boolean tick() {
        this.ticksRemaining--;

        if (this.ticksRemaining <= 0) {
            return true;
        }

        this.ticksSinceLastSpawn++;

        if (this.ticksSinceLastSpawn >= this.spawnInterval) {
            this.ticksSinceLastSpawn = 0;

            ServerPlayerEntity player = findPlayer(this.playerName);
            if (player != null) {
                spawnFirework(player);
            }
        }

        return this.ticksRemaining <= 0;
    }

    @Override
    public Text getDisplayName() {
        if (this.bossBarName != null) {
            return this.bossBarName;
        }

        return Text.translatableWithFallback(
                "event." + MOD_ID + ".fireworks.display_name",
                "Firework Event for player: %1$s",
                this.playerName);
    }

    @Override
    public float getProgress() {
        if (this.initialDuration <= 0)
            return 0;
        return (float) this.ticksRemaining / this.initialDuration;
    }

    @Override
    public BossBar.Color getBossBarColor() {
        return BossBar.Color.RED;
    }

    private ServerPlayerEntity findPlayer(String playerName) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            logger.error("FireworkEvent: Player '{}' not found", playerName);
        }
        return player;
    }

    private <T> T getArg(String[] args, int index, T defaultValue, String paramName,
            java.util.function.Function<String, T> converter) {
        if (args.length <= index)
            return defaultValue;

        try {
            return converter.apply(args[index]);
        } catch (NumberFormatException e) {
            logger.warn("FireworkEvent: Invalid {}, using default", paramName);
            return defaultValue;
        }
    }

    private void spawnFirework(ServerPlayerEntity player) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d pos = getRandomPosition(playerPos);

        ItemStack fireworkStack = createRandomFirework();

        var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, pos.x, pos.y, pos.z, fireworkStack);

        world.spawnEntity(firework);
    }

    private Vec3d getRandomPosition(Vec3d basePos) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * radius;
        double offsetX = distance * Math.cos(angle);
        double offsetZ = distance * Math.sin(angle);

        return basePos.add(offsetX, 1, offsetZ);
    }

    private ItemStack createRandomFirework() {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        List<FireworkExplosionComponent> explosions = new ArrayList<>();

        int explosionCount = random.nextInt(3) + 1;
        for (int i = 0; i < explosionCount; i++) {
            FireworkExplosionComponent.Type type = FireworkExplosionComponent.Type.byId(random.nextInt(5));
            IntList colors = generateRandomColors(random.nextInt(5) + 1);
            IntList fadeColors = generateRandomColors(random.nextInt(5) + 1);
            boolean hasTrail = random.nextBoolean();
            boolean hasTwinkle = random.nextBoolean();

            explosions.add(new FireworkExplosionComponent(type, colors, fadeColors, hasTrail, hasTwinkle));
        }

        FireworksComponent fireworks = new FireworksComponent(random.nextInt(2) + 1, explosions);
        firework.set(DataComponentTypes.FIREWORKS, fireworks);
        return firework;
    }

    private IntList generateRandomColors(int count) {
        int[] colorsArray = new int[count];
        for (int j = 0; j < count; j++) {
            colorsArray[j] = random.nextInt(0xFFFFFF);
        }
        return IntList.of(colorsArray);
    }
}
