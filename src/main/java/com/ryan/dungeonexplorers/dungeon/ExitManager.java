package com.ryan.dungeonexplorers.dungeon;

import com.ryan.dungeonexplorers.managers.ScoreManager;
import com.ryan.dungeonexplorers.managers.TimerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.stream.Collectors;

public class ExitManager implements Listener {

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
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!scoreManager.isSessionActive()) return;
        if (!timerManager.isRunning()) return;
        if (transitioning) return;

        Player player = event.getPlayer();

        if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) return;
        if (!standingOnGoldPlate(player)) return;

        checkStageClear();
    }

    private void checkStageClear() {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < 3000) return;

        Set<Player> activePlayers = Bukkit.getOnlinePlayers()
                .stream()
                .filter(p -> scoreManager.getSession().hasPlayer(p.getUniqueId()))
                .collect(Collectors.toSet());

        if (activePlayers.isEmpty()) return;

        boolean allReady = activePlayers.stream()
                .allMatch(this::standingOnGoldPlate);

        if (!allReady) return;

        lastTrigger = now;
        triggerNextStage();
    }

    private boolean standingOnGoldPlate(Player player) {
        Block feet = player.getLocation().getBlock();
        Block below = player.getLocation().clone().subtract(0, 0.2, 0).getBlock();
        Block oneBelow = player.getLocation().clone().subtract(0, 1.0, 0).getBlock();

        return feet.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                || below.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                || oneBelow.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
    }

    private void triggerNextStage() {
        transitioning = true;

        Bukkit.broadcastMessage("§aAll players ready! Advancing stage...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            timerManager.clearCurrentLevelWithCurrentTime();
            transitioning = false;
        }, 40L);
    }
}

