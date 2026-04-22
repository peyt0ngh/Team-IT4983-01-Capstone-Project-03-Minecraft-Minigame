package com.ryan.dungeoncrawler;

import com.ryan.dungeoncrawler.listeners.EntityDeathListener;
import com.ryan.dungeoncrawler.listeners.PlayerDeathListener;
import com.ryan.dungeoncrawler.listeners.PlayerJoinListener;
import com.ryan.dungeoncrawler.listeners.RoomListener;
import com.ryan.dungeoncrawler.managers.ScoreManager;
import com.ryan.dungeoncrawler.managers.TimerManager;
import com.ryan.dungeoncrawler.util.TreasureItemFactory;
import com.ryan.dungeoncrawler.game.RoomManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DungeonCrawler — unified plugin class.
 *
 * Merges the MinigameScorer (scoring, treasures, deaths) and the
 * Timer System (per-level countdown, difficulty scaling, mob spawning).
 *
 * Wiring overview:
 *  - TimerManager holds a RunState (level + timeRemaining + difficultyIndex).
 *  - ScoreManager holds a GameSession (player scores, stage time-bonuses).
 *  - When a level expires (or is manually cleared), TimerManager calls
 *    scoreManager.completeStage(secondsRemaining) to bank the time bonus
 *    before advancing to the next level.
 *  - GameSession.TOTAL_STAGES == RunState's maximum level count (both 3).
 */
public class DungeonCrawlerPlugin extends JavaPlugin {

    private ScoreManager scoreManager;
    private TimerManager timerManager;

    @Override
    public void onEnable() {
        getLogger().info("DungeonCrawler enabling...");

        // Initialise treasure item utility (needs plugin for NamespacedKey).
        TreasureItemFactory.init(this);

        // Create managers — TimerManager needs ScoreManager to bank time bonuses.
        scoreManager = new ScoreManager(this);
        timerManager = new TimerManager(this, scoreManager);
        
        new ExitManager(this);
        
        RoomManager roomManager = new RoomManager(this);

            getServer().getPluginManager().registerEvents(
                new RoomListener(roomManager),
                this
            );

        // Register event listeners.
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new EntityDeathListener(scoreManager), this);
        pm.registerEvents(new PlayerDeathListener(scoreManager, timerManager), this);
        pm.registerEvents(new PlayerJoinListener(scoreManager), this);

        // Register commands — all routed through one executor.
        DungeonCommandExecutor executor = new DungeonCommandExecutor(this, scoreManager, timerManager);
        for (String cmd : new String[]{
                "mgstart", "mgstop", "mgscore", "mgstageclear", "mgaddtreasure"}) {
            var cmdObj = getCommand(cmd);
            if (cmdObj != null) cmdObj.setExecutor(executor);
        }

        getLogger().info("DungeonCrawler enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Clean up the timer task so it doesn't outlive the plugin.
        if (timerManager != null) {
            timerManager.stopRun();
        }
        if (scoreManager != null && scoreManager.isSessionActive()) {
            getLogger().warning("Server shut down with an active session — scores were not saved.");
        }
        getLogger().info("DungeonCrawler disabled.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ScoreManager getScoreManager() { return scoreManager; }
    public TimerManager getTimerManager() { return timerManager; }
}


