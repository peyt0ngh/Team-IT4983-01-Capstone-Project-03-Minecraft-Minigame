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
 * <p><b>Singleplayer:</b> the first death ends the run immediately (timer
 * stopped, session tallied and cleared).</p>
 *
 * <p><b>Multiplayer:</b> deaths are recorded for the penalty; the run
 * continues until all stages are cleared or the timer expires.</p>
 *
 * <p><b>Game-over kills:</b> when {@link TimerManager} calls
 * {@code player.setHealth(0)} as part of a timer-expiry game-over, the session
 * is already being torn down inside {@code TimerManager.gameOver()}. This
 * listener guards against that by checking {@link ScoreManager#isSessionActive()}
 * before doing anything — by the time the death event fires, the session may
 * already be null.</p>
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
        // Session may have been cleared by TimerManager.gameOver() before this
        // event fires — guard against NPE / double-end.
        if (!scoreManager.isSessionActive()) return;

        Player      player  = event.getEntity();
        GameSession session = scoreManager.getSession();

        // ── Singleplayer: first death ends the run ────────────────────────────
        if (session.isSingleplayer()) {
            scoreManager.recordDeath(player);
            player.sendMessage(Component.text(
                    "You died! The dungeon run is ending...", NamedTextColor.RED));

            timerManager.stopRun();
            scoreManager.tallyTreasuresFromInventories();

            JavaPlugin plugin = (JavaPlugin) player.getServer()
                    .getPluginManager().getPlugin("DungeonCrawler");
            MessageUtil.broadcastFinalResults(plugin, session);
            scoreManager.clearSession();
            return;
        }

        // ── Multiplayer: record penalty, notify player ────────────────────────
        scoreManager.recordDeath(player);
        PlayerScore ps = session.getScore(player.getUniqueId());
        if (ps != null) {
            MessageUtil.sendInfo(player,
                    "Death recorded! -20 pts | Total deaths: " + ps.getDeaths());
        }
    }
}
