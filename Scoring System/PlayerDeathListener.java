package com.minigame.scorer.listeners;

import com.minigame.scorer.managers.ScoreManager;
import com.minigame.scorer.model.GameSession;
import com.minigame.scorer.model.PlayerScore;
import com.minigame.scorer.util.MessageUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listens for player deaths to apply the -20 point death penalty.
 *
 * In singleplayer mode the penalty only ever applies once (the session ends
 * on death anyway, but the deduction is still capped defensively).
 */
public class PlayerDeathListener implements Listener {

    private final ScoreManager scoreManager;

    public PlayerDeathListener(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!scoreManager.isSessionActive()) return;

        Player player = event.getEntity();
        GameSession session = scoreManager.getSession();

        // In singleplayer, the game ends on first death — notify and stop.
        if (session.isSingleplayer()) {
            scoreManager.recordDeath(player);
            player.sendMessage(
                net.kyori.adventure.text.Component.text(
                    "You died! The game is ending...", NamedTextColor.RED));
            // Tally treasures and display final results.
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(
                (org.bukkit.plugin.java.JavaPlugin) player.getServer()
                    .getPluginManager().getPlugin("MinigameScorer"),
                session);
            scoreManager.clearSession();
            return;
        }

        // Multiplayer: record death and notify the player.
        scoreManager.recordDeath(player);
        PlayerScore ps = session.getScore(player.getUniqueId());
        if (ps != null) {
            MessageUtil.sendInfo(player,
                "Death recorded! -20 pts | Total deaths: " + ps.getDeaths());
        }
    }
}
