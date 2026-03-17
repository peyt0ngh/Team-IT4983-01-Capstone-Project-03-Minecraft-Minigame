package com.ryan.dungeoncrawler.listeners;

import com.ryan.dungeoncrawler.managers.ScoreManager;
import com.ryan.dungeoncrawler.managers.TimerManager;
import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.model.PlayerScore;
import com.ryan.dungeoncrawler.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for player deaths to apply the -20 point death penalty.
 *
 * In singleplayer mode the game ends on the first death; both the timer and
 * the score session are stopped and final results are broadcast.
 *
 * Integration note: TimerManager is injected here so that /stopRun() is
 * called alongside scoreManager.clearSession() in the singleplayer branch,
 * preventing the timer from continuing after the game has ended.
 */
public class PlayerDeathListener implements Listener {

    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    public PlayerDeathListener(ScoreManager scoreManager, TimerManager timerManager) {
        this.scoreManager = scoreManager;
        this.timerManager = timerManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!scoreManager.isSessionActive()) return;

        Player player = event.getEntity();
        GameSession session = scoreManager.getSession();

        // In singleplayer, the game ends on first death.
        if (session.isSingleplayer()) {
            scoreManager.recordDeath(player);
            player.sendMessage(Component.text(
                    "You died! The dungeon run is ending...", NamedTextColor.RED));

            // Stop the countdown timer before tallying scores.
            timerManager.stopRun();

            scoreManager.tallyTreasuresFromInventories();

            // Retrieve the plugin instance to pass to MessageUtil.
            JavaPlugin plugin = (JavaPlugin) player.getServer()
                    .getPluginManager().getPlugin("DungeonCrawler");

            MessageUtil.broadcastFinalResults(plugin, session);
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
