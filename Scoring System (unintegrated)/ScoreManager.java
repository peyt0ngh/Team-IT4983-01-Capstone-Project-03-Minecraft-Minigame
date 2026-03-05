package com.minigame.scorer.managers;

import com.minigame.scorer.model.GameSession;
import com.minigame.scorer.model.PlayerScore;
import com.minigame.scorer.model.TreasureRarity;
import com.minigame.scorer.util.TreasureItemFactory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Central manager that orchestrates game sessions and score operations.
 */
public class ScoreManager {

    /** Probability (0.0–1.0) that a slain mob drops a treasure. */
    private static final double TREASURE_DROP_CHANCE = 0.05; // 5 %

    private final JavaPlugin plugin;
    private GameSession currentSession = null;

    public ScoreManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public boolean isSessionActive() {
        return currentSession != null;
    }

    public GameSession getSession() {
        return currentSession;
    }

    /**
     * Start a new session, registering every online player automatically.
     */
    public void startSession(boolean singleplayer) {
        currentSession = new GameSession(singleplayer);
        plugin.getServer().getOnlinePlayers().forEach(p ->
                currentSession.addPlayer(p.getUniqueId(), p.getName()));
    }

    /**
     * End the current session and return it (for displaying results).
     * The caller is responsible for clearing the reference via {@link #clearSession()}.
     */
    public GameSession endSession() {
        return currentSession;
    }

    public void clearSession() {
        currentSession = null;
    }

    // ── Scoring actions ───────────────────────────────────────────────────────

    /**
     * Award kill points to a player based on the mob's max health.
     */
    public void recordKill(Player killer, LivingEntity mob) {
        if (!isSessionActive()) return;
        PlayerScore ps = getOrCreate(killer);

        double maxHealth = mob.getAttribute(
                org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        int pts = pointsForHealth(maxHealth);
        ps.addKillPoints(pts);
    }

    /**
     * Possibly drop a treasure from a slain mob (5 % chance).
     * If triggered, a random rarity is chosen (weighted) and the item is
     * dropped at the mob's location.
     */
    public void handleMobTreasureDrop(LivingEntity mob) {
        if (!isSessionActive()) return;
        if (Math.random() >= TREASURE_DROP_CHANCE) return;

        TreasureRarity rarity = rollTreasureRarity();
        mob.getWorld().dropItemNaturally(
                mob.getLocation(),
                TreasureItemFactory.createTreasureItem(rarity));
    }

    /**
     * Award a player a treasure item directly into their inventory
     * (used by the /mgaddtreasure command).
     */
    public void grantTreasure(Player player, TreasureRarity rarity) {
        if (!isSessionActive()) return;
        player.getInventory().addItem(TreasureItemFactory.createTreasureItem(rarity));
        // Points are tallied at game-end when inventories are scanned.
    }

    /**
     * Scan every participant's inventory and accumulate treasure points.
     * Called once when the session ends.
     */
    public void tallyTreasuresFromInventories() {
        if (!isSessionActive()) return;
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            PlayerScore ps = currentSession.getScore(player.getUniqueId());
            if (ps == null) return;

            for (var stack : player.getInventory().getContents()) {
                if (stack == null) continue;
                TreasureRarity rarity = TreasureItemFactory.getRarityFromItem(stack);
                if (rarity != null) {
                    // Treasures don't stack, so each slot == 1 treasure.
                    ps.addTreasurePoints(rarity.getPoints());
                }
            }
        });
    }

    /**
     * Record a player death and apply the -20 point deduction.
     */
    public void recordDeath(Player player) {
        if (!isSessionActive()) return;
        PlayerScore ps = getOrCreate(player);
        ps.incrementDeaths();
    }

    // ── Stage time ────────────────────────────────────────────────────────────

    /**
     * Bank remaining seconds for the current stage and advance the stage counter.
     */
    public boolean completeStage(int secondsRemaining) {
        if (!isSessionActive()) return false;
        return currentSession.completeCurrentStage(secondsRemaining);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerScore getOrCreate(Player player) {
        currentSession.addPlayer(player.getUniqueId(), player.getName());
        return currentSession.getScore(player.getUniqueId());
    }

    /**
     * Map a mob's max-health value to the appropriate point tier.
     *
     * ≤ 10 hp  → +1
     * 11–20    → +3
     * 21–30    → +5
     * 31–40    → +7
     * 41+      → +9
     */
    public static int pointsForHealth(double maxHealth) {
        if (maxHealth <= 10) return 1;
        if (maxHealth <= 20) return 3;
        if (maxHealth <= 30) return 5;
        if (maxHealth <= 40) return 7;
        return 9;
    }

    /**
     * Weighted random roll for a treasure rarity on mob drop.
     * Weights: Common 50 %, Uncommon 30 %, Rare 15 %, Super Rare 5 %.
     */
    private TreasureRarity rollTreasureRarity() {
        double roll = Math.random();
        if (roll < 0.50) return TreasureRarity.COMMON;
        if (roll < 0.80) return TreasureRarity.UNCOMMON;
        if (roll < 0.95) return TreasureRarity.RARE;
        return TreasureRarity.SUPER_RARE;
    }
}
