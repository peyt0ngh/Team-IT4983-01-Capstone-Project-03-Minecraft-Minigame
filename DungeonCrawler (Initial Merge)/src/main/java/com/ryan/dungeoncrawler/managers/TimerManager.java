package com.ryan.dungeoncrawler.managers;

import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.util.MessageUtil;
import com.ryan.dungeoncrawler.util.SchematicUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class TimerManager {

    public static final int TOTAL_LEVELS = 3;

    private final Plugin plugin;
    private final ScoreManager scoreManager;

    private int currentLevel = 0;
    private int timeRemaining = 0;
    private int taskId = -1;

    private final World world;
    private final Location pasteLocation;

    public TimerManager(Plugin plugin, ScoreManager scoreManager) {
        this.plugin = plugin;
        this.scoreManager = scoreManager;

        this.world = Bukkit.getWorld("world");
        this.pasteLocation = new Location(world, 0, 70, 0);
    }

    /* ===================================================== */
    /* PUBLIC API                                            */
    /* ===================================================== */

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

    /* ===================================================== */
    /* TIMER                                                 */
    /* ===================================================== */

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

    /* ===================================================== */
    /* STAGE LOADING                                         */
    /* ===================================================== */

    private void loadStage(int stage) {

        clearArena();

        File folder = new File(plugin.getDataFolder(),
                "maps/stage" + stage);

        if (!folder.exists()) folder.mkdirs();

        File[] schems = folder.listFiles((dir, name) ->
                name.endsWith(".schem") || name.endsWith(".schematic"));

        if (schems == null || schems.length == 0) {
            plugin.getLogger().warning(
                    "No schematics found in " + folder.getPath());
            return;
        }

        File chosen = schems[new Random().nextInt(schems.length)];

        SchematicUtil.pasteSchematic(
                chosen,
                pasteLocation,
                0
        );

        Bukkit.broadcast(Component.text(
                "Loaded map: " + chosen.getName(),
                NamedTextColor.GRAY
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleportPlayersToSpawn();
        }, 20L);
    }

    /* ===================================================== */
    /* PLAYER TELEPORT                                       */
    /* ===================================================== */

    private void teleportPlayersToSpawn() {

        Location spawn = findSpawnMarker();

        if (spawn == null) {
            spawn = pasteLocation.clone().add(8, 1, 8);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!scoreManager.isSessionActive()) continue;

            if (!scoreManager.getSession()
                    .hasPlayer(player.getUniqueId())) continue;

            player.teleport(spawn);
        }
    }

    private Location findSpawnMarker() {

        int minX = pasteLocation.getBlockX();
        int minY = pasteLocation.getBlockY();
        int minZ = pasteLocation.getBlockZ();

        for (int x = minX; x < minX + 64; x++) {
            for (int y = minY; y < minY + 30; y++) {
                for (int z = minZ; z < minZ + 64; z++) {

                    Block b = world.getBlockAt(x, y, z);

                    if (b.getType() == Material.LIME_CONCRETE) {
                        return b.getLocation().add(0.5, 1.1, 0.5);
                    }
                }
            }
        }

        return null;
    }

    /* ===================================================== */
    /* ARENA RESET                                           */
    /* ===================================================== */

    private void clearArena() {

        for (int x = -10; x <= 80; x++) {
            for (int y = 60; y <= 120; y++) {
                for (int z = -10; z <= 80; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    /* ===================================================== */
    /* END STATES                                            */
    /* ===================================================== */

    private void finishRun() {

        stopRun();

        Bukkit.broadcast(Component.text(
                "🏆 Dungeon Cleared!",
                NamedTextColor.GOLD
        ));

        if (scoreManager.isSessionActive()) {
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(
                    (org.bukkit.plugin.java.JavaPlugin) plugin,
                    scoreManager.getSession()
            );
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

            MessageUtil.broadcastFinalResults(
                    (org.bukkit.plugin.java.JavaPlugin) plugin,
                    session
            );

            scoreManager.clearSession();
        }
    }

    /* ===================================================== */
    /* HELPERS                                               */
    /* ===================================================== */

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
