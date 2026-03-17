package com.minigame.scorer;

import com.minigame.scorer.managers.ScoreManager;
import com.minigame.scorer.model.GameSession;
import com.minigame.scorer.model.TreasureRarity;
import com.minigame.scorer.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Handles all commands registered in plugin.yml.
 *
 * Commands:
 *   /mgstart   <singleplayer|multiplayer>
 *   /mgstop
 *   /mgscore
 *   /mgstageclear <seconds>
 *   /mgaddtreasure <player> <rarity>
 */
public class ScorerCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;

    public ScorerCommandExecutor(JavaPlugin plugin, ScoreManager scoreManager) {
        this.plugin       = plugin;
        this.scoreManager = scoreManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        return switch (command.getName().toLowerCase()) {
            case "mgstart"      -> handleStart(sender, args);
            case "mgstop"       -> handleStop(sender);
            case "mgscore"      -> handleScore(sender);
            case "mgstageclear" -> handleStageClear(sender, args);
            case "mgaddtreasure"-> handleAddTreasure(sender, args);
            default             -> false;
        };
    }

    // ── /mgstart ──────────────────────────────────────────────────────────────

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minigamescorer.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to start a game.");
            return true;
        }
        if (scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "A session is already running. Use /mgstop first.");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.sendError(sender, "Usage: /mgstart <singleplayer|multiplayer>");
            return true;
        }

        boolean sp = args[0].equalsIgnoreCase("singleplayer")
                  || args[0].equalsIgnoreCase("sp");
        scoreManager.startSession(sp);

        String mode = sp ? "Singleplayer" : "Multiplayer";
        plugin.getServer().broadcast(
            net.kyori.adventure.text.Component.text(
                "⚔ Minigame started! Mode: " + mode + " | Good luck!",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
        return true;
    }

    // ── /mgstop ───────────────────────────────────────────────────────────────

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("minigamescorer.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to stop a game.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        scoreManager.tallyTreasuresFromInventories();
        GameSession session = scoreManager.endSession();
        MessageUtil.broadcastFinalResults(plugin, session);
        scoreManager.clearSession();
        return true;
    }

    // ── /mgscore ──────────────────────────────────────────────────────────────

    private boolean handleScore(CommandSender sender) {
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }
        MessageUtil.sendScoreboard(sender, scoreManager.getSession());
        return true;
    }

    // ── /mgstageclear ─────────────────────────────────────────────────────────

    private boolean handleStageClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minigamescorer.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to use this command.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.sendError(sender, "Usage: /mgstageclear <seconds>");
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            MessageUtil.sendError(sender, "Invalid number: " + args[0]);
            return true;
        }

        GameSession session = scoreManager.getSession();
        if (session.allStagesDone()) {
            MessageUtil.sendError(sender, "All " + GameSession.TOTAL_STAGES + " stages already completed.");
            return true;
        }

        int stageBefore = session.getCurrentStage() + 1; // 1-based label
        boolean ok = scoreManager.completeStage(seconds);
        if (ok) {
            plugin.getServer().broadcast(
                net.kyori.adventure.text.Component.text(
                    "⏱ Stage " + stageBefore + " cleared with "
                    + seconds + "s remaining! +" + seconds + " pts for all.",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA));
        }
        return true;
    }

    // ── /mgaddtreasure ────────────────────────────────────────────────────────

    private boolean handleAddTreasure(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minigamescorer.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to use this command.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }
        if (args.length < 2) {
            MessageUtil.sendError(sender, "Usage: /mgaddtreasure <player> <common|uncommon|rare|superrare>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendError(sender, "Player '" + args[0] + "' not found.");
            return true;
        }

        TreasureRarity rarity = TreasureRarity.fromString(args[1]);
        if (rarity == null) {
            MessageUtil.sendError(sender, "Unknown rarity '" + args[1]
                + "'. Use: common, uncommon, rare, superrare.");
            return true;
        }

        scoreManager.grantTreasure(target, rarity);
        MessageUtil.sendSuccess(sender,
            "Gave " + rarity.getDisplayName() + " treasure to " + target.getName() + ".");
        MessageUtil.sendInfo(target,
            "You received a " + rarity.getDisplayName() + " treasure!");
        return true;
    }
}
