package com.ryan.dungeoncrawler.mobs;

import com.ryan.dungeoncrawler.dungeon.RoomEncounter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Random;

/**
 * Spawns waves of level-appropriate mobs into a {@link RoomEncounter} and
 * monitors them until the room is cleared.
 *
 * Mob selection is driven by {@link EncounterConfig}: each call to
 * {@link #start(RoomEncounter, List, int)} picks a random environment for the
 * given level and alternates between the environment's two mob types.
 *
 * Special handling:
 *  - {@link org.bukkit.entity.Slime} and {@link org.bukkit.entity.MagmaCube}
 *    are spawned at SIZE 2 (medium) as requested.
 *  - Mob count is driven by {@link com.ryan.dungeoncrawler.DifficultyStats}
 *    via the caller (TimerManager) when used in a level-advance context,
 *    or defaults to 6 per room encounter here.
 */
public class WaveRoomManager {

    private static final int DEFAULT_SPAWN_COUNT = 6;
    private static final int SLIME_SIZE          = 2; // medium
    private static final int CHECK_PERIOD_TICKS  = 40; // 2 s

    private final Plugin plugin;
    private final Random random = new Random();

    public WaveRoomManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start a room encounter using the default spawn count.
     * Uses {@link #DEFAULT_SPAWN_COUNT} mobs and picks an environment for level 1.
     */
    public void start(RoomEncounter room, List<Player> players) {
        start(room, players, 1);
    }

    /**
     * Start a room encounter for a specific dungeon level.
     *
     * @param room    the encounter to populate
     * @param players players involved (used for future targeting logic)
     * @param level   1-based dungeon level used to select the environment pool
     */
    public void start(RoomEncounter room, List<Player> players, int level) {
        EncounterConfig.Environment env = EncounterConfig.randomForLevel(level);

        // Announce the environment to all online players.
        Bukkit.broadcast(Component.text(
                "☠ Entering: " + env.name(), NamedTextColor.DARK_RED));

        World world  = room.getCenter().getWorld();
        Location center = room.getCenter();

        spawnMobs(world, center, env, DEFAULT_SPAWN_COUNT);

        // Poll until all monsters in the room radius are dead.
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            boolean mobsAlive = world
                    .getNearbyEntities(room.getCenter(), 10, 10, 10)
                    .stream()
                    .anyMatch(e -> e instanceof Monster);

            if (!mobsAlive) {
                room.setCleared(true);
                task.cancel();
            }
        }, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    /**
     * Spawn {@code count} mobs from the given environment, alternating between
     * primary and secondary mob types with a small random spread.
     */
    private void spawnMobs(World world, Location center,
                           EncounterConfig.Environment env, int count) {
        for (int i = 0; i < count; i++) {
            EntityType type = (i % 2 == 0) ? env.primary() : env.secondary();

            double ox = (random.nextDouble() - 0.5) * 4; // ±2 blocks
            double oz = (random.nextDouble() - 0.5) * 4;
            Location loc = center.clone().add(ox, 0, oz);

            spawnEntity(world, loc, type);
        }
    }

    /**
     * Spawn a single entity, applying special sizing for slime variants.
     */
    @SuppressWarnings("deprecation")
    private void spawnEntity(World world, Location loc, EntityType type) {
        try {
            var entity = world.spawnEntity(loc, type);

            // Size 2 (medium) for Slime and Magma Cube.
            if (entity instanceof Slime slime) {
                slime.setSize(SLIME_SIZE);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Failed to spawn entity " + type + " at " + loc + ": " + e.getMessage());
        }
    }
}
