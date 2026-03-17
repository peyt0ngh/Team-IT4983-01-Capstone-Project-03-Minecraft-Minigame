package com.ryan.dungeoncrawler.listeners;

import com.ryan.dungeoncrawler.managers.ScoreManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Automatically registers players who join mid-session.
 */
public class PlayerJoinListener implements Listener {

    private final ScoreManager scoreManager;

    public PlayerJoinListener(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!scoreManager.isSessionActive()) return;
        scoreManager.getSession().addPlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName());
    }
}
