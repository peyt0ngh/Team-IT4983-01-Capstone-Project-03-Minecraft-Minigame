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

/**
 * Handles all commands registered in plugin.yml.
 *
 * Commands:
 *   /mgstart   <singleplayer|multiplayer>  — starts session + timer
 *   /mgstop                                — stops session + timer, shows results
 *   /mgscore                               — shows live scoreboard
 *   /mgstageclear <seconds>                — clears current level with time bonus
 *   /mgaddtreasure <player> <rarity>       — gives a treasure to a player
 *
 * Both ScoreManager and TimerManager are coordinated here so that
 * /mgstart and /mgstop keep both systems in sync.
 */
public class DungeonCommandExecutor implements CommandExecutor {

    private final JavaPlugin  plugin;
    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    public DungeonCommandExecutor(JavaPlugin plugin,
                                  ScoreManager scoreManager,
                                  TimerManager timerManager) {
        this.plugin       = plugin;
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

    // ── /mgstart ──────────────────────────────────────────────────────────────

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dungeoncrawler.admin")) {
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

    // 1. Start scoring
    scoreManager.startSession(sp);
    
    // 2. Start timer
    timerManager.startRun();
    
    // 3. Generate dungeon
    com.ryan.dungeoncrawler.dungeon.RoomRegistry.clear();
    
    com.ryan.dungeoncrawler.dungeon.DungeonGenerator generator =
            new com.ryan.dungeoncrawler.dungeon.DungeonGenerator(plugin);
    
    // Stage 1 = 6x6 grid
    var rooms = generator.generate(6);
    
    // 4. Teleport all players into dungeon start
    for (Player p : plugin.getServer().getOnlinePlayers()) {
        p.teleport(rooms.get(0).getCenter());
    }

        String mode = sp ? "Singleplayer" : "Multiplayer";
        plugin.getServer().broadcast(Component.text(
                "⚔ Dungeon Crawler started! Mode: " + mode + " | Good luck!",
                NamedTextColor.GREEN));
        return true;
    }

    // ── /mgstop ───────────────────────────────────────────────────────────────

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to stop a game.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }

        // 1. Stop the timer first so no further level-advance callbacks fire.
        timerManager.stopRun();

        // 2. Tally treasures and broadcast final scores.
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

    /**
     * Manually complete the current level and bank time-bonus seconds.
     *
     * Delegates to {@link TimerManager#clearCurrentLevel(int)}, which in turn
     * calls {@link ScoreManager#completeStage(int)} — keeping both systems
     * in sync through a single code path.
     */
    private boolean handleStageClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to use this command.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }
        if (!timerManager.isRunning()) {
            MessageUtil.sendError(sender, "Timer is not running.");
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
            MessageUtil.sendError(sender,
                    "All " + GameSession.TOTAL_STAGES + " stages already completed.");
            return true;
        }

        // TimerManager handles the level advance and calls scoreManager.completeStage().
        timerManager.clearCurrentLevel(seconds);
        return true;
    }

    // ── /mgaddtreasure ────────────────────────────────────────────────────────

    private boolean handleAddTreasure(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dungeoncrawler.admin")) {
            MessageUtil.sendError(sender, "You don't have permission to use this command.");
            return true;
        }
        if (!scoreManager.isSessionActive()) {
            MessageUtil.sendError(sender, "No active session.");
            return true;
        }
        if (args.length < 2) {
            MessageUtil.sendError(sender,
                    "Usage: /mgaddtreasure <player> <common|uncommon|rare|superrare>");
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
