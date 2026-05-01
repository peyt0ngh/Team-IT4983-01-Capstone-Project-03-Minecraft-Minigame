package com.ryan.dungeonexplorers.listeners;

import com.ryan.dungeonexplorers.managers.ScoreManager;
import com.ryan.dungeonexplorers.managers.TimerManager;
import com.ryan.dungeonexplorers.model.GameSession;
import com.ryan.dungeonexplorers.model.PlayerScore;
import com.ryan.dungeonexplorers.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    public PlayerDeathListener(JavaPlugin plugin,
                               ScoreManager scoreManager,
                               TimerManager timerManager) {
        this.plugin = plugin;
        this.scoreManager = scoreManager;
        this.timerManager = timerManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!scoreManager.isSessionActive()) return;

        Player player = event.getEntity();
        GameSession session = scoreManager.getSession();

        scoreManager.recordDeath(player);

        if (session.isSingleplayer()) {
            player.sendMessage(Component.text(
                    "You died! The dungeon run is ending...",
                    NamedTextColor.RED));

            timerManager.stopRun();
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, session);
            scoreManager.clearSession();
            return;
        }

        PlayerScore ps = session.getScore(player.getUniqueId());
        if (ps != null) {
            MessageUtil.sendInfo(player,
                    "Death recorded! -20 pts | Total deaths: " + ps.getDeaths());
        }
    }
}
