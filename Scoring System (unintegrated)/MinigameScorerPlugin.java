package com.minigame.scorer;

import com.minigame.scorer.listeners.EntityDeathListener;
import com.minigame.scorer.listeners.PlayerDeathListener;
import com.minigame.scorer.listeners.PlayerJoinListener;
import com.minigame.scorer.managers.ScoreManager;
import com.minigame.scorer.util.TreasureItemFactory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MinigameScorer — main plugin class.
 *
 * Wires together all managers, listeners, and commands.
 */
public class MinigameScorerPlugin extends JavaPlugin {

    private ScoreManager scoreManager;

    @Override
    public void onEnable() {
        getLogger().info("MinigameScorer enabling...");

        // Initialise utilities that need the plugin instance.
        TreasureItemFactory.init(this);

        // Create the central score manager.
        scoreManager = new ScoreManager(this);

        // Register event listeners.
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new EntityDeathListener(scoreManager), this);
        pm.registerEvents(new PlayerDeathListener(scoreManager), this);
        pm.registerEvents(new PlayerJoinListener(scoreManager), this);

        // Register commands — all routed through one executor.
        ScorerCommandExecutor executor = new ScorerCommandExecutor(this, scoreManager);
        for (String cmd : new String[]{"mgstart", "mgstop", "mgscore", "mgstageclear", "mgaddtreasure"}) {
            var cmdObj = getCommand(cmd);
            if (cmdObj != null) cmdObj.setExecutor(executor);
        }

        getLogger().info("MinigameScorer enabled successfully.");
    }

    @Override
    public void onDisable() {
        // If a session is running when the server shuts down, print scores to console.
        if (scoreManager != null && scoreManager.isSessionActive()) {
            getLogger().warning("Server shut down with an active session — scores were not saved.");
        }
        getLogger().info("MinigameScorer disabled.");
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }
}
