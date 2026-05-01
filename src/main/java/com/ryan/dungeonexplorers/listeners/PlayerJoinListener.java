package com.ryan.dungeonexplorers.listeners;

import com.ryan.dungeonexplorers.managers.ScoreManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final ScoreManager scoreManager;

    public PlayerJoinListener(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (!scoreManager.isSessionActive()) return;

        // Do NOT add them to the session mid-run
        event.getPlayer().sendMessage(
                "§eA dungeon run is already in progress. Please wait for the next round."
        );
    }
}
