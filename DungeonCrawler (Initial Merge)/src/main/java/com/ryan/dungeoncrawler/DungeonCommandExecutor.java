package com.ryan.dungeoncrawler;

import com.ryan.dungeoncrawler.managers.ScoreManager;
import com.ryan.dungeoncrawler.managers.TimerManager;
import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.model.TreasureRarity;
import com.ryan.dungeoncrawler.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class DungeonCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    public DungeonCommandExecutor(JavaPlugin plugin,
                                  ScoreManager scoreManager,
                                  TimerManager timerManager) {
        this.plugin = plugin;
        this.scoreManager = scoreManager;
        this.timerManager = timerManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        return switch (command.getName().toLowerCase()) {
            case "mgstart"       -> handleStart(sender, args);
            case "mgstop"        -> handleStop(sender);
            case "mgscore"       -> handleScore(sender);
            case "mgstageclear"  -> handleStageClear(sender, args);
            case "mgaddtreasure" -> handleAddTreasure(sender, args);
            default              -> false;
        };
    }

    /* ====================================================== */
    /* START                                                  */
    /* ====================================================== */

    private boolean handleStart(CommandSender sender, String[] args) {

        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "No permission.");
            return true;
        }

        if (scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "Session already running.");
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendError(sender,
                    "Usage: /mgstart <singleplayer|multiplayer>");
            return true;
        }

        boolean sp = args[0].equalsIgnoreCase("singleplayer")
                || args[0].equalsIgnoreCase("sp");

        // Start session FIRST
        scoreManager.startSession(sp);

        // Then start timer (which loads dungeon + teleports players)
        timerManager.startRun();

        String mode = sp ? "Singleplayer" : "Multiplayer";

        plugin.getServer().broadcast(Component.text(
                "⚔ Dungeon started! Mode: " + mode,
                NamedTextColor.GREEN));

        return true;
    }

    /* ====================================================== */
    /* STOP                                                   */
    /* ====================================================== */

    private boolean handleStop(CommandSender sender) {

        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "No permission.");
            return true;
        }

        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        timerManager.stopRun();

        scoreManager.tallyTreasuresFromInventories();
        GameSession session = scoreManager.endSession();

        MessageUtil.broadcastFinalResults(plugin, session);

        scoreManager.clearSession();

        return true;
    }

    /* ====================================================== */
    /* SCORE                                                  */
    /* ====================================================== */

    private boolean handleScore(CommandSender sender) {

        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        MessageUtil.sendScoreboard(sender, scoreManager.getSession());
        return true;
    }

    /* ====================================================== */
    /* FORCE STAGE CLEAR                                      */
    /* ====================================================== */

    private boolean handleStageClear(CommandSender sender, String[] args) {

        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "No permission.");
            return true;
        }

        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        if (!timerManager.isRunning()) {
            MessageUtil.sendError(sender, "Timer not running.");
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendError(sender,
                    "Usage: /mgstageclear <seconds>");
            return true;
        }

        int seconds;

        try {
            seconds = Integer.parseInt(args[0]);
        } catch (Exception e) {
            MessageUtil.sendError(sender, "Invalid number.");
            return true;
        }

        timerManager.clearCurrentLevel(seconds);

        return true;
    }

    /* ====================================================== */
    /* TREASURE                                               */
    /* ====================================================== */

    private boolean handleAddTreasure(CommandSender sender, String[] args) {

        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "No permission.");
            return true;
        }

        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendError(sender,
                    "Usage: /mgaddtreasure <player> <rarity>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);

        if (target == null) {
            MessageUtil.sendError(sender, "Player not found.");
            return true;
        }

        TreasureRarity rarity = TreasureRarity.fromString(args[1]);

        if (rarity == null) {
            MessageUtil.sendError(sender, "Invalid rarity.");
            return true;
        }

        scoreManager.grantTreasure(target, rarity);

        MessageUtil.sendSuccess(sender,
                "Gave " + rarity.getDisplayName()
                        + " to " + target.getName());

        return true;
    }
}
