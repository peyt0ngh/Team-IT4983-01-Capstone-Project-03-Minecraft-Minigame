package com.ryan.dungeoncrawler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

public class TimerManager {

    private final Plugin plugin;
    private RunState state;
    private int taskId = -1;

    private final DifficultyManager difficultyManager = new DifficultyManager();

    public TimerManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void startRun() {
        state = new RunState(1, 60, 1); // Level 1, 60 seconds, difficulty index 1
        startTimerLoop();
    }

    private void startTimerLoop() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            state.tick();

            Bukkit.broadcastMessage("Time left: " + state.getTimeRemaining());

            if (state.getTimeRemaining() <= 0) {
                handleTimeExpired();
            }

        }, 20L, 20L); // 1 second
    }

    private void handleTimeExpired() {
        Bukkit.broadcastMessage("Time's up on Level " + state.getLevel() + "!");

        // Move to next level
        state.nextLevel(60); // resets time to 60 seconds

        // Get difficulty stats for this new level
        DifficultyStats stats = difficultyManager.getStatsForLevel(state.getLevel());

        Bukkit.broadcastMessage("Advancing to Level " + state.getLevel() +
                " | Health: " + stats.getMobHealth() +
                " | Damage: " + stats.getMobDamage() +
                " | Mob Count: " + stats.getMobCount());

        // Spawn mobs using these stats
        spawnMobsForLevel(stats);
    }

    private void spawnMobsForLevel(DifficultyStats stats) {
        Location spawn = Bukkit.getWorld("world").getSpawnLocation(); // Replace with dungeon room

        for (int i = 0; i < stats.getMobCount(); i++) {
            Zombie z = spawn.getWorld().spawn(spawn, Zombie.class);

            z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(stats.getMobHealth());
            z.setHealth(stats.getMobHealth());

            z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(stats.getMobDamage());
        }
    }

    public void stopRun() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}