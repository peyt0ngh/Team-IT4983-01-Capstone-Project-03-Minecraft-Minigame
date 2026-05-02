package com.ryan.dungeonexplorers;

import com.ryan.dungeonexplorers.dungeon.ExitManager;
import com.ryan.dungeonexplorers.listeners.EntityDeathListener;
import com.ryan.dungeonexplorers.listeners.PlayerDeathListener;
import com.ryan.dungeonexplorers.listeners.PlayerJoinListener;
import com.ryan.dungeonexplorers.listeners.RespawnListener;
import com.ryan.dungeonexplorers.managers.ScoreManager;
import com.ryan.dungeonexplorers.managers.TimerManager;
import com.ryan.dungeonexplorers.util.TreasureItemFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DungeonExplorersPlugin extends JavaPlugin {

    private ScoreManager scoreManager;
    private TimerManager timerManager;

    @Override
    public void onEnable() {
        getLogger().info("dungeonexplorers enabling...");

        TreasureItemFactory.init(this);

        scoreManager = new ScoreManager(this);
        timerManager = new TimerManager(this, scoreManager);

        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new ExitManager(this, scoreManager, timerManager), this);

        pm.registerEvents(new EntityDeathListener(scoreManager), this);
        pm.registerEvents(new RespawnListener(this, timerManager, scoreManager), this);
        pm.registerEvents(new PlayerDeathListener(this, scoreManager, timerManager), this);
        pm.registerEvents(new PlayerJoinListener(scoreManager), this);

        DungeonCommandExecutor executor =
                new DungeonCommandExecutor(this, scoreManager, timerManager);

        registerCommand("mgstart", executor);
        registerCommand("mgstop", executor);
        registerCommand("mgscore", executor);
        registerCommand("mgstageclear", executor);
        registerCommand("mgaddtreasure", executor);

        getLogger().info("dungeonexplorers enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (timerManager != null) {
            timerManager.stopRun();
        }

        getLogger().info("dungeonexplorers disabled.");
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
