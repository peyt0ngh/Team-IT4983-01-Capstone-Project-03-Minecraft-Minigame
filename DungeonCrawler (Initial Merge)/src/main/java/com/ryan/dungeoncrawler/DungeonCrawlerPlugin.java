package com.ryan.dungeoncrawler;

import com.ryan.dungeoncrawler.dungeon.ExitManager;
import com.ryan.dungeoncrawler.listeners.EntityDeathListener;
import com.ryan.dungeoncrawler.listeners.PlayerDeathListener;
import com.ryan.dungeoncrawler.listeners.PlayerJoinListener;
import com.ryan.dungeoncrawler.managers.ScoreManager;
import com.ryan.dungeoncrawler.managers.TimerManager;
import com.ryan.dungeoncrawler.util.TreasureItemFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DungeonCrawlerPlugin extends JavaPlugin {

    private ScoreManager scoreManager;
    private TimerManager timerManager;

    @Override
    public void onEnable() {
        getLogger().info("DungeonCrawler enabling...");

        TreasureItemFactory.init(this);

        scoreManager = new ScoreManager(this);
        timerManager = new TimerManager(this, scoreManager);

        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new ExitManager(this, scoreManager, timerManager), this);

        pm.registerEvents(new EntityDeathListener(scoreManager), this);
        pm.registerEvents(new PlayerDeathListener(this, scoreManager, timerManager), this);
        pm.registerEvents(new PlayerJoinListener(scoreManager), this);

        DungeonCommandExecutor executor =
                new DungeonCommandExecutor(this, scoreManager, timerManager);

        registerCommand("mgstart", executor);
        registerCommand("mgstop", executor);
        registerCommand("mgscore", executor);
        registerCommand("mgstageclear", executor);
        registerCommand("mgaddtreasure", executor);

        getLogger().info("DungeonCrawler enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (timerManager != null) {
            timerManager.stopRun();
        }

        getLogger().info("DungeonCrawler disabled.");
    }

    private void registerCommand(String name, DungeonCommandExecutor exec) {
        PluginCommand cmd = getCommand(name);

        if (cmd == null) {
            getLogger().warning("Command missing in plugin.yml: " + name);
            return;
        }

        cmd.setExecutor(exec);
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }
}
