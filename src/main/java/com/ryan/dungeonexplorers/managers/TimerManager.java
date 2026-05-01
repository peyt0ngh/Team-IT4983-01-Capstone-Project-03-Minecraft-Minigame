package com.ryan.dungeonexplorers.managers;

import com.ryan.dungeonexplorers.model.GameSession;
import com.ryan.dungeonexplorers.util.MessageUtil;
import com.ryan.dungeonexplorers.util.SchematicUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class TimerManager {

    public static final int TOTAL_LEVELS = 3;

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;

    private int currentLevel = 0;
    private int timeRemaining = 0;
    private int taskId = -1;

    private final World world;
    private final Location pasteLocation;

    private final Set<UUID> waitingPlayers = new HashSet<>();

    public TimerManager(JavaPlugin plugin, ScoreManager scoreManager) {
        this.plugin = plugin;
        this.scoreManager = scoreManager;

        this.world = Bukkit.getWorld("world");
        this.pasteLocation = new Location(world, 1000, -60, 0);
    }

    public void startRun() {
        stopRun();

        currentLevel = 1;
        loadStage(currentLevel);
        startTimer(stageSeconds(currentLevel));
    }

    public void stopRun() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isRunning() {
        return taskId != -1;
    }

    public boolean clearCurrentLevelWithCurrentTime() {
        return clearCurrentLevel(timeRemaining);
    }

    public boolean clearCurrentLevel(int secondsRemaining) {
        if (!isRunning()) return false;

        stopRun();

        scoreManager.completeStage(secondsRemaining);

        Bukkit.broadcast(Component.text(
                "✔ Stage " + currentLevel + " cleared!",
                NamedTextColor.GREEN
        ));

        if (currentLevel >= TOTAL_LEVELS) {
            finishRun();
            return true;
        }

        currentLevel++;
        loadStage(currentLevel);
        startTimer(stageSeconds(currentLevel));

        return true;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }

    public void sendToWaitingArea(Player player) {
        waitingPlayers.add(player.getUniqueId());

        Location waiting = new Location(world, 1200, -55, 0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.spigot().respawn();
            player.teleport(waiting);
            player.sendMessage("§eYou are out for this stage. You will rejoin next stage.");
        }, 2L);
    }

    private void startTimer(int seconds) {
        timeRemaining = seconds;

        Bukkit.broadcast(Component.text(
                "⚔ Stage " + currentLevel + " begins! Time: " + format(seconds),
                NamedTextColor.YELLOW
        ));

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            timeRemaining--;

            if (timeRemaining % 60 == 0 ||
                    (timeRemaining <= 30 && timeRemaining % 10 == 0) ||
                    timeRemaining <= 5) {

                Bukkit.broadcast(Component.text(
                        "⏱ " + format(timeRemaining) + " remaining",
                        NamedTextColor.AQUA
                ));
            }

            if (timeRemaining <= 0) {
                failRun();
            }

        }, 20L, 20L);
    }

    private void loadStage(int stage) {
        File folder = new File(plugin.getDataFolder(), "maps/stage" + stage);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] schems = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".schem")
                        || name.toLowerCase().endsWith(".schematic"));

        if (schems == null || schems.length == 0) {
            plugin.getLogger().warning("No schematics found in " + folder.getPath());
            return;
        }

        File chosen = schems[new Random().nextInt(schems.length)];

        // FIX (lag): Remove non-player entities synchronously first (fast list filter),
        // then fill ~21 million blocks to AIR on an async thread so the main thread
        // never stalls. Only once the fill completes do we paste + scan markers back
        // on the main thread where Bukkit API calls are safe.
        clearEntitiesSync();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            clearBlocksAsync();

            Bukkit.getScheduler().runTask(plugin, () -> {
                SchematicUtil.pasteSchematic(chosen, pasteLocation, 0);

                Bukkit.broadcast(Component.text(
                        "Loaded map: " + chosen.getName(),
                        NamedTextColor.GRAY
                ));

                // Give the schematic one tick to finish placing blocks before we scan
                // for lime/red concrete markers.
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        spawnMobsAndTeleportAsync(stage, chosen.getName()), 20L);
            });
        });
    }

    /**
     * Removes non-player entities in the arena bounds.
     * Must be called on the main thread.
     */
    private void clearEntitiesSync() {
        if (world == null) return;

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - 300;
        int maxX = baseX + 300;
        int minY = baseY;
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + 120);
        int minZ = baseZ - 300;
        int maxZ = baseZ + 300;

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();

            if (loc.getX() >= minX && loc.getX() <= maxX &&
                    loc.getY() >= minY && loc.getY() <= maxY &&
                    loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                entity.remove();
            }
        }
    }

    /**
     * Fills arena blocks to AIR.
     * Safe to call from an async thread — world.getBlockAt + setType are the only
     * calls here, and we are not touching any entity or player state.
     *
     * NOTE: world.getBlockAt on Paper/Spigot is safe from async threads for block
     * reads; setType triggers chunk-dirty marking which is also thread-safe on modern
     * Paper. If your server build prohibits async block writes, replace this body
     * with a runTask back to the main thread and batch via a repeating task instead.
     */
    private void clearBlocksAsync() {
        if (world == null) return;

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - 300;
        int maxX = baseX + 300;
        int minY = baseY;
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + 120);
        int minZ = baseZ - 300;
        int maxZ = baseZ + 300;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * Scans for lime concrete (spawn) and red concrete (mob) markers async,
     * then bounces entity spawning and player teleportation back to the main thread.
     */
    private void spawnMobsAndTeleportAsync(int stage, String schematicName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int baseX = pasteLocation.getBlockX();
            int baseY = pasteLocation.getBlockY();
            int baseZ = pasteLocation.getBlockZ();

            int radius = 300;
            int minY = Math.max(world.getMinHeight(), baseY);
            int maxY = Math.min(world.getMaxHeight() - 1, baseY + 120);

            // Collect marker positions off the main thread.
            List<Location> limeMarkers = new ArrayList<>();
            List<Location> redMarkers = new ArrayList<>();

            for (int x = baseX - radius; x <= baseX + radius; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        Material type = block.getType();

                        if (type == Material.LIME_CONCRETE) {
                            limeMarkers.add(block.getLocation());
                        } else if (type == Material.RED_CONCRETE) {
                            redMarkers.add(block.getLocation().add(0.5, 1.0, 0.5));
                        }
                    }
                }
            }

            // All actual Bukkit mutations must happen on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                // --- Spawn mobs at red concrete markers ---
                for (Location spawnLoc : redMarkers) {
                    // Remove the marker block.
                    world.getBlockAt(
                            spawnLoc.getBlockX(),
                            spawnLoc.getBlockY() - 1,
                            spawnLoc.getBlockZ()
                    ).setType(Material.AIR);

                    for (EntityType type : chooseMobsForStageAndMap(stage, schematicName)) {
                        try {
                            Entity spawned = world.spawnEntity(spawnLoc, type);
                            if (spawned instanceof LivingEntity living) {
                                living.setRemoveWhenFarAway(false);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(
                                    "Could not spawn mob " + type + ": " + e.getMessage());
                        }
                    }
                }

                // --- Compute spawn point from lime concrete markers ---
                Location spawn;

                if (limeMarkers.isEmpty()) {
                    plugin.getLogger().warning("No lime spawn marker found. Using fallback spawn.");
                    spawn = pasteLocation.clone().add(8, 2, 8);
                } else {
                    double avgX = 0, avgY = 0, avgZ = 0;
                    for (Location loc : limeMarkers) {
                        avgX += loc.getX();
                        avgY += loc.getY();
                        avgZ += loc.getZ();
                    }
                    avgX /= limeMarkers.size();
                    avgY /= limeMarkers.size();
                    avgZ /= limeMarkers.size();

                    // FIX (void spawn): The old while loop had wrong operator precedence and
                    // never incremented `raised`, so it could loop past solid ground and drop
                    // players into the void, or exit early leaving them inside a block.
                    // Fixed: parentheses enforce both-feet-clear check; raised++ caps iterations.
                    spawn = new Location(world, avgX + 0.5, avgY + 1, avgZ + 0.5);

                    int maxRaise = 10;
                    int raised = 0;

                    while (raised < maxRaise && (
                            !spawn.getBlock().getType().isAir()
                            || !spawn.clone().add(0, 1, 0).getBlock().getType().isAir())) {
                        spawn.add(0, 1, 0);
                        raised++;
                    }
                }

                // --- Teleport session players to spawn ---
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!scoreManager.isSessionActive()) continue;
                    if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) continue;

                    player.teleport(spawn);
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    player.setFireTicks(0);
                }

                waitingPlayers.clear();
            });
        });
    }

    private List<EntityType> chooseMobsForStageAndMap(int stage, String schematicName) {
        String name = schematicName.toLowerCase();

        // Special override maps:
        // mansion_4 gets only 2 Creakings.
        if (name.contains("mansion_4")) {
            return List.of(
                    EntityType.CREAKING,
                    EntityType.CREAKING
            );
        }

        // Ancient City maps get only 1 Warden.
        if (name.contains("ancient_city")) {
            return List.of(EntityType.WARDEN);
        }

        List<EntityType> mobs = new ArrayList<>();

        // Every normal map gets one default mob.
        mobs.add(randomDefaultMob());

        // And one environment-specific mob.
        switch (stage) {
            case 1 -> mobs.add(chooseStageOneThemeMob(name));
            case 2 -> mobs.add(chooseStageTwoThemeMob(name));
            case 3 -> mobs.add(chooseStageThreeThemeMob(name));
            default -> mobs.add(randomDefaultMob());
        }

        return mobs;
    }

    private EntityType randomDefaultMob() {
        return Math.random() < 0.5
                ? EntityType.ZOMBIE
                : EntityType.SKELETON;
    }

    private EntityType chooseStageOneThemeMob(String name) {
        if (name.contains("stronghold")) {
            return Math.random() < 0.5
                    ? EntityType.SPIDER
                    : EntityType.CAVE_SPIDER;
        }

        if (name.contains("temple")) {
            return Math.random() < 0.5
                    ? EntityType.PARCHED
                    : EntityType.HUSK;
        }

        if (name.contains("trial")) {
            return Math.random() < 0.5
                    ? EntityType.BOGGED
                    : EntityType.BREEZE;
        }

        return randomDefaultMob();
    }

    private EntityType chooseStageTwoThemeMob(String name) {
        if (name.contains("mansion")) {
            return Math.random() < 0.5
                    ? EntityType.VINDICATOR
                    : EntityType.PILLAGER;
        }

        if (name.contains("fortress")) {
            return Math.random() < 0.5
                    ? EntityType.WITHER_SKELETON
                    : EntityType.BLAZE;
        }

        if (name.contains("ancient_city")) {
            return EntityType.WARDEN;
        }

        return randomDefaultMob();
    }

    private EntityType chooseStageThreeThemeMob(String name) {
        if (name.contains("end_city")) {
            return Math.random() < 0.5
                    ? EntityType.ENDERMAN
                    : EntityType.ENDERMITE;
        }

        if (name.contains("monument")) {
            return Math.random() < 0.5
                    ? EntityType.DROWNED
                    : EntityType.BOGGED;
        }

        if (name.contains("bastion")) {
            return Math.random() < 0.5
                    ? EntityType.MAGMA_CUBE
                    : EntityType.WITHER_SKELETON;
        }

        return randomDefaultMob();
    }

    private void finishRun() {
        stopRun();

        Bukkit.broadcast(Component.text(
                "🏆 Dungeon Cleared!",
                NamedTextColor.GOLD
        ));

        if (scoreManager.isSessionActive()) {
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, scoreManager.getSession());
            scoreManager.clearSession();
        }
    }

    private void failRun() {
        stopRun();

        Bukkit.broadcast(Component.text(
                "☠ Time expired! Dungeon failed.",
                NamedTextColor.DARK_RED
        ));

        if (scoreManager.isSessionActive()) {
            GameSession session = scoreManager.getSession();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (session.hasPlayer(player.getUniqueId())) {
                    player.setHealth(0);
                }
            }

            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, session);
            scoreManager.clearSession();
        }
    }

    private int stageSeconds(int level) {
        return switch (level) {
            case 1 -> 240;
            case 2 -> 360;
            case 3 -> 480;
            default -> 240;
        };
    }

    private String format(int total) {
        if (total < 0) total = 0;

        int m = total / 60;
        int s = total % 60;

        return m + ":" + String.format("%02d", s);
    }
}
