package com.ryan.dungeonexplorers.listeners;

import com.ryan.dungeonexplorers.managers.ScoreManager;
import com.ryan.dungeonexplorers.managers.TimerManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RespawnListener implements Listener {

    private final JavaPlugin plugin;
    private final TimerManager timerManager;
    private final ScoreManager scoreManager;

    public RespawnListener(JavaPlugin plugin, TimerManager timerManager, ScoreManager scoreManager) {
        this.plugin = plugin;
        this.timerManager = timerManager;
        this.scoreManager = scoreManager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Only affects players registered in the active session.
        if (!scoreManager.isSessionActive()) return;
        if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) return;

        // Only redirect respawns while a stage is actively running.
        if (!timerManager.isRunning()) return;

        Location spawn = timerManager.getCurrentSpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
