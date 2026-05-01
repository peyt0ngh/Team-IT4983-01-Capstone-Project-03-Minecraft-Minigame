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

    // How many blocks to set AIR per tick during the arena clear.
    // 50 000 keeps each tick well under 50 ms even on modest hardware.
    private static final int BLOCKS_PER_TICK = 50_000;
    private static final int ARENA_RADIUS = 150;
    // Y ceiling relative to pasteLocation.getBlockY(). paste=-60, so max scanned Y = -60+20 = -40.
    private static final int ARENA_HEIGHT = 20;

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

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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

    public int getCurrentLevel() { return currentLevel; }
    public int getTimeRemaining() { return timeRemaining; }

    public void sendToWaitingArea(Player player) {
        waitingPlayers.add(player.getUniqueId());

        Location waiting = new Location(world, 1200, -55, 0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.spigot().respawn();
            player.teleport(waiting);
            player.sendMessage("§eYou are out for this stage. You will rejoin next stage.");
        }, 2L);
    }

    // -------------------------------------------------------------------------
    // Timer
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Stage loading  (all Bukkit calls stay on the main thread)
    // -------------------------------------------------------------------------

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

        // Remove entities immediately (fast — just a list walk).
        clearEntities();

        // Drain block clears across ticks so no single tick freezes the server.
        // The callback fires on the main thread once the last batch completes,
        // so pasteSchematic and all marker scanning remain fully thread-safe.
        clearBlocksBatched(() -> {
            SchematicUtil.pasteSchematic(chosen, pasteLocation, 0);

            Bukkit.broadcast(Component.text(
                    "Loaded map: " + chosen.getName(),
                    NamedTextColor.GRAY
            ));

            // One tick after paste so WorldEdit/FAWE finishes writing chunks
            // before we scan for markers.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnMobsFromMarkers(stage, chosen.getName());
                teleportPlayersToSpawn();
            }, 20L);
        });
    }

    /**
     * Removes all non-player entities inside the arena bounds.
     * Must be called on the main thread.
     */
    private void clearEntities() {
        if (world == null) return;

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - ARENA_RADIUS;
        int maxX = baseX + ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;
        int minZ = baseZ - ARENA_RADIUS;
        int maxZ = baseZ + ARENA_RADIUS;

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
     * Clears all arena blocks by processing BLOCKS_PER_TICK positions per tick,
     * keeping the main thread responsive. Calls {@code onFinished} once done.
     *
     * Everything stays on the main thread — moving world access off-thread caused
     * the dungeon not to generate and markers not to be found, because Bukkit's
     * world API is not thread-safe.
     */
    private void clearBlocksBatched(Runnable onFinished) {
        if (world == null) {
            onFinished.run();
            return;
        }

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - ARENA_RADIUS;
        int maxX = baseX + ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;
        int minZ = baseZ - ARENA_RADIUS;
        int maxZ = baseZ + ARENA_RADIUS;

        int totalY = maxY - minY + 1;
        int totalZ = maxZ - minZ + 1;
        int totalBlocks = (maxX - minX + 1) * totalY * totalZ;

        int[] cursor = {0};
        int[] clearTaskId = {-1};

        clearTaskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int processed = 0;

            while (cursor[0] < totalBlocks && processed < BLOCKS_PER_TICK) {
                int idx = cursor[0];

                // Decode flat index back to (x, y, z).
                int lx = idx / (totalY * totalZ);
                int remainder = idx % (totalY * totalZ);
                int ly = remainder / totalZ;
                int lz = remainder % totalZ;

                // false = skip physics/lighting updates during bulk fill, much faster.
                world.getBlockAt(minX + lx, minY + ly, minZ + lz)
                        .setType(Material.AIR, false);

                cursor[0]++;
                processed++;
            }

            if (cursor[0] >= totalBlocks) {
                Bukkit.getScheduler().cancelTask(clearTaskId[0]);
                onFinished.run();
            }

        }, 0L, 1L);
    }

    // -------------------------------------------------------------------------
    // Marker scanning  (all on main thread — world reads are only safe here)
    // -------------------------------------------------------------------------

    /**
     * Scans for red concrete mob-spawn markers, spawns the mobs, then removes
     * the marker blocks.
     */
    private void spawnMobsFromMarkers(int stage, String schematicName) {
        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int radius = ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;

        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {

                    Block block = world.getBlockAt(x, y, z);

                    if (block.getType() == Material.RED_CONCRETE) {
                        Location spawnLoc = block.getLocation().add(0.5, 1.0, 0.5);

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

                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private void teleportPlayersToSpawn() {
        Location spawn = findSpawnMarker();

        if (spawn == null) {
            plugin.getLogger().warning("Falling back to default spawn offset.");
            spawn = pasteLocation.clone().add(8, 2, 8);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!scoreManager.isSessionActive()) continue;
            if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) continue;

            player.teleport(spawn);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setFireTicks(0);
        }

        waitingPlayers.clear();
    }

    /**
     * Locates lime concrete spawn marker(s) placed in the schematic and returns
     * a safe two-block-tall standing position above them.
     *
     * FIX: The original while loop had two bugs that caused players to fall into
     * the void or spawn inside blocks:
     *   1. Missing parentheses meant operator precedence evaluated the second
     *      air-check outside the `raised < maxRaise` guard entirely.
     *   2. `raised` was never incremented, making the cap completely useless.
     */
    private Location findSpawnMarker() {
        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int radius = ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;

        List<Location> markers = new ArrayList<>();

        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() == Material.LIME_CONCRETE) {
                        markers.add(b.getLocation());
                    }
                }
            }
        }

        if (markers.isEmpty()) {
            plugin.getLogger().warning("No lime spawn marker found.");
            return null;
        }

        double avgX = 0, avgY = 0, avgZ = 0;
        for (Location loc : markers) {
            avgX += loc.getX();
            avgY += loc.getY();
            avgZ += loc.getZ();
        }
        avgX /= markers.size();
        avgY /= markers.size();
        avgZ /= markers.size();

        Location spawn = new Location(world, avgX + 0.5, avgY + 1, avgZ + 0.5);

        int maxRaise = 10;
        int raised = 0;

        // FIX: parentheses ensure both checks are guarded by raised < maxRaise.
        // FIX: raised++ actually enforces the cap now.
        while (raised < maxRaise && (
                !spawn.getBlock().getType().isAir()
                || !spawn.clone().add(0, 1, 0).getBlock().getType().isAir())) {
            spawn.add(0, 1, 0);
            raised++;
        }

        return spawn;
    }

    // -------------------------------------------------------------------------
    // Mob selection
    // -------------------------------------------------------------------------

    private List<EntityType> chooseMobsForStageAndMap(int stage, String schematicName) {
        String name = schematicName.toLowerCase();

        if (name.contains("mansion_4")) {
            return List.of(EntityType.CREAKING, EntityType.CREAKING);
        }

        if (name.contains("ancient_city")) {
            return List.of(EntityType.WARDEN);
        }

        List<EntityType> mobs = new ArrayList<>();
        mobs.add(randomDefaultMob());

        switch (stage) {
            case 1 -> mobs.add(chooseStageOneThemeMob(name));
            case 2 -> mobs.add(chooseStageTwoThemeMob(name));
            case 3 -> mobs.add(chooseStageThreeThemeMob(name));
            default -> mobs.add(randomDefaultMob());
        }

        return mobs;
    }

    private EntityType randomDefaultMob() {
        return Math.random() < 0.5 ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    private EntityType chooseStageOneThemeMob(String name) {
        if (name.contains("stronghold"))
            return Math.random() < 0.5 ? EntityType.SPIDER : EntityType.CAVE_SPIDER;
        if (name.contains("temple"))
            return Math.random() < 0.5 ? EntityType.PARCHED : EntityType.HUSK;
        if (name.contains("trial"))
            return Math.random() < 0.5 ? EntityType.BOGGED : EntityType.BREEZE;
        return randomDefaultMob();
    }

    private EntityType chooseStageTwoThemeMob(String name) {
        if (name.contains("mansion"))
            return Math.random() < 0.5 ? EntityType.VINDICATOR : EntityType.PILLAGER;
        if (name.contains("fortress"))
            return Math.random() < 0.5 ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
        if (name.contains("ancient_city"))
            return EntityType.WARDEN;
        return randomDefaultMob();
    }

    private EntityType chooseStageThreeThemeMob(String name) {
        if (name.contains("end_city"))
            return Math.random() < 0.5 ? EntityType.ENDERMAN : EntityType.ENDERMITE;
        if (name.contains("monument"))
            return Math.random() < 0.5 ? EntityType.DROWNED : EntityType.BOGGED;
        if (name.contains("bastion"))
            return Math.random() < 0.5 ? EntityType.MAGMA_CUBE : EntityType.WITHER_SKELETON;
        return randomDefaultMob();
    }

    // -------------------------------------------------------------------------
    // Run end states
    // -------------------------------------------------------------------------

    private void finishRun() {
        stopRun();

        Bukkit.broadcast(Component.text("🏆 Dungeon Cleared!", NamedTextColor.GOLD));

        if (scoreManager.isSessionActive()) {
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, scoreManager.getSession());
            scoreManager.clearSession();
        }
    }

    private void failRun() {
        stopRun();

        Bukkit.broadcast(Component.text("☠ Time expired! Dungeon failed.", NamedTextColor.DARK_RED));

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
