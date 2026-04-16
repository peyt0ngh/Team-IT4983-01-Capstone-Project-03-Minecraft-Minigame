package com.ryan.dungeoncrawler.util;

import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.model.PlayerScore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class for sending consistently formatted messages to players.
 *
 * Fixes applied vs. initial merge:
 *  - Corrected malformed {@code String.format} call in {@code buildScoreboardLines}
 *    (missing {@code rank} arg and misplaced comma/semicolon).
 *  - Gold colour now applied to rank 1, yellow to rank 2 (was inverted).
 */
public final class MessageUtil {

    private MessageUtil() {}

    private static final Component DIVIDER = Component.text(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            NamedTextColor.DARK_GRAY);

    // ── Generic helpers ───────────────────────────────────────────────────────

    public static void send(CommandSender sender, String msg, NamedTextColor color) {
        sender.sendMessage(Component.text(msg, color));
    }

    public static void sendError(CommandSender sender, String msg) {
        send(sender, "✘ " + msg, NamedTextColor.RED);
    }

    public static void sendSuccess(CommandSender sender, String msg) {
        send(sender, "✔ " + msg, NamedTextColor.GREEN);
    }

    public static void sendInfo(CommandSender sender, String msg) {
        send(sender, "ℹ " + msg, NamedTextColor.YELLOW);
    }

    // ── Score display ─────────────────────────────────────────────────────────

    public static void broadcastScoreboard(JavaPlugin plugin, GameSession session) {
        buildScoreboardLines(session, "— LIVE SCOREBOARD —")
                .forEach(l -> plugin.getServer().broadcast(l));
    }

    public static void broadcastFinalResults(JavaPlugin plugin, GameSession session) {
        buildScoreboardLines(session, "— FINAL RESULTS —")
                .forEach(l -> plugin.getServer().broadcast(l));
    }

    public static void sendScoreboard(CommandSender sender, GameSession session) {
        buildScoreboardLines(session, "— SCOREBOARD —")
                .forEach(sender::sendMessage);
    }

    // ── Private builders ──────────────────────────────────────────────────────

    private static List<Component> buildScoreboardLines(GameSession session, String title) {
        List<Component> lines = new ArrayList<>();
        int     timeBonus = session.getTotalTimeBonus();
        boolean sp        = session.isSingleplayer();

        lines.add(DIVIDER);
        lines.add(Component.text("  " + title)
                           .color(NamedTextColor.GOLD)
                           .decoration(TextDecoration.BOLD, true));
        lines.add(Component.text(
                "  Mode: " + (sp ? "Singleplayer" : "Multiplayer")
                + " | Stage: " + Math.min(session.getCurrentStage(), GameSession.TOTAL_STAGES)
                + "/" + GameSession.TOTAL_STAGES
                + " | Time bonus: +" + timeBonus + " pts")
                .color(NamedTextColor.GRAY));
        lines.add(DIVIDER);

        List<PlayerScore> sorted = new ArrayList<>(session.getAllScores());
        sorted.sort(Comparator.comparingInt(ps -> -ps.calculateScore(timeBonus, sp)));

        int rank = 1;
        for (PlayerScore ps : sorted) {
            int total = ps.calculateScore(timeBonus, sp);

            // Colour: gold for 1st, yellow for 2nd, white otherwise.
            NamedTextColor rowColor = switch (rank) {
                case 1  -> NamedTextColor.GOLD;
                case 2  -> NamedTextColor.YELLOW;
                default -> NamedTextColor.WHITE;
            };

            lines.add(Component.text(String.format(
                    "  #%d %-16s %5d pts  [K:%d T:%d D:%d]",
                    rank,
                    ps.getPlayerName(),
                    total,
                    ps.getKillPoints(),
                    ps.getTreasurePoints(),
                    ps.getDeaths()))
                    .color(rowColor));
            rank++;
        }

        lines.add(DIVIDER);
        lines.add(Component.text("  K=Kill pts  T=Treasure pts  D=Deaths",
                NamedTextColor.DARK_GRAY));
        lines.add(DIVIDER);
        return lines;
    }
}
