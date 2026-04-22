package com.ryan.dungeoncrawler.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class ExitManager {

    private final JavaPlugin plugin;

    public ExitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startChecking();
    }

    private void startChecking() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            Set<Player> playersOnPlates = new HashSet<>();
            Set<Player> activePlayers = new HashSet<>(Bukkit.getOnlinePlayers());

            for (Player player : activePlayers) {

                Block block = player.getLocation().getBlock();

                if (isPressurePlate(block.getType())) {
                    playersOnPlates.add(player);
                }
            }

            // All players must be on plates
            if (!activePlayers.isEmpty() &&
                playersOnPlates.size() == activePlayers.size()) {

                triggerNextStage();
            }

        }, 20L, 20L); // check every second
    }

    private boolean isPressurePlate(Material mat) {
        return mat.name().contains("PRESSURE_PLATE");
    }

    private void triggerNextStage() {

        Bukkit.broadcastMessage("§aAll players ready! Advancing to next stage...");

        // TODO: Hook into your TimerManager / Stage system
        // Example:
        // timerManager.clearCurrentLevel(0);

    }
}
