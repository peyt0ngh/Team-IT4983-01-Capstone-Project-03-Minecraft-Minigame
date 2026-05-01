package com.ryan.dungeoncrawler.managers;

import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.util.MessageUtil;
import com.ryan.dungeoncrawler.util.SchematicUtil;
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
        clearArena();

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

        SchematicUtil.pasteSchematic(chosen, pasteLocation, 0);

        Bukkit.broadcast(Component.text(
                "Loaded map: " + chosen.getName(),
                NamedTextColor.GRAY
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnMobsFromMarkers(stage);
            teleportPlayersToSpawn();
        }, 20L);
    }

    private void teleportPlayersToSpawn() {
        Location spawn = findSpawnMarker();

        if (spawn == null) {
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

    private Location findSpawnMarker() {
        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        List<Location> markers = new ArrayList<>();

        int radius = 300;

        int minY = Math.max(world.getMinHeight(), baseY);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + 120);

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
            plugin.getLogger().warning("No lime spawn marker found. Using fallback spawn.");
            return null;
        }

        double avgX = 0;
        double avgY = 0;
        double avgZ = 0;

        for (Location loc : markers) {
            avgX += loc.getX();
            avgY += loc.getY();
            avgZ += loc.getZ();
        }

        avgX /= markers.size();
        avgY /= markers.size();
        avgZ /= markers.size();

        Location spawn = new Location(world, avgX + 0.5, avgY + 1, avgZ + 0.5);

        while (!spawn.getBlock().getType().isAir()
                || !spawn.clone().add(0, 1, 0).getBlock().getType().isAir()) {
            spawn.add(0, 1, 0);
        }

        return spawn;
    }

    private void spawnMobsFromMarkers(int stage) {
        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int radius = 300;

        int minY = Math.max(world.getMinHeight(), baseY);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + 120);

        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {

                    Block block = world.getBlockAt(x, y, z);

                    if (block.getType() == Material.RED_CONCRETE) {

                        Location spawnLoc = block.getLocation().add(0.5, 1.0, 0.5);

                        EntityType type = chooseMobForStage(stage);

                        Entity spawned = world.spawnEntity(spawnLoc, type);

                        if (spawned instanceof LivingEntity living) {
                            living.setRemoveWhenFarAway(false);
                        }

                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private EntityType chooseMobForStage(int stage) {
        return switch (stage) {
            case 1 -> Math.random() < 0.5
                    ? EntityType.ZOMBIE
                    : EntityType.SKELETON;

            case 2 -> Math.random() < 0.5
                    ? EntityType.WITHER_SKELETON
                    : EntityType.BLAZE;

            case 3 -> Math.random() < 0.5
                    ? EntityType.ENDERMAN
                    : EntityType.SHULKER;

            default -> EntityType.ZOMBIE;
        };
    }

    private void clearArena() {
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
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

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
