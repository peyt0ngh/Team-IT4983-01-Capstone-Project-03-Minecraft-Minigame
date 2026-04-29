package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.managers.ScoreManager;
import com.ryan.dungeoncrawler.managers.TimerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExitManager {

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    private boolean transitioning = false;
    private long lastTrigger = 0L;

    public ExitManager(JavaPlugin plugin,
                       ScoreManager scoreManager,
                       TimerManager timerManager) {

        this.plugin = plugin;
        this.scoreManager = scoreManager;
        this.timerManager = timerManager;

        startChecking();
    }

    private void startChecking() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (transitioning) return;

            if (!scoreManager.isSessionActive()) return;

            Set<Player> activePlayers = Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> scoreManager.getSession()
                            .hasPlayer(p.getUniqueId()))
                    .collect(Collectors.toSet());

            if (activePlayers.isEmpty()) return;

            long now = System.currentTimeMillis();

            if (now - lastTrigger < 3000) return;

            boolean allReady = activePlayers.stream()
                    .allMatch(this::standingOnGoldPlate);

            if (allReady) {
                triggerNextStage();
                lastTrigger = now;
            }

        }, 20L, 20L);
    }

    private boolean standingOnGoldPlate(Player player) {
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
    
        return blockBelow.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
    }

    private void triggerNextStage() {

        transitioning = true;

        Bukkit.broadcastMessage(
                "§aAll players ready! Advancing stage..."
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            timerManager.clearCurrentLevel(0);

            transitioning = false;

        }, 40L); // 2 second delay
    }
}
