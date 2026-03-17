package com.ryan.dungeoncrawler.managers;

import com.ryan.dungeoncrawler.DifficultyManager;
import com.ryan.dungeoncrawler.DifficultyStats;
import com.ryan.dungeoncrawler.RunState;
import com.ryan.dungeoncrawler.model.GameSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages the per-level countdown timer and difficulty-scaled mob spawning.
 *
 * Integration with ScoreManager:
 *  - When a level's timer expires naturally, the remaining seconds (0) are
 *    banked via {@link ScoreManager#completeStage(int)}.
 *  - When an admin runs /mgstageclear <seconds>, {@link #clearCurrentLevel(int)}
 *    is called, which banks the provided seconds and immediately advances the level.
 *  - Both paths share {@link #advanceLevel(int)} to avoid duplication.
 *
 * Level count matches {@link GameSession#TOTAL_STAGES} (both 3).
 * After all levels are complete the timer stops automatically.
 */
public class TimerManager {

    /** Seconds allocated to each level. */
    public static final int SECONDS_PER_LEVEL = 60;

    /** Total number of dungeon levels — must equal GameSession.TOTAL_STAGES. */
    public static final int TOTAL_LEVELS = GameSession.TOTAL_STAGES; // 3

    private final Plugin plugin;
    private final ScoreManager scoreManager;
    private final DifficultyManager difficultyManager = new DifficultyManager();

    private RunState state   = null;
    private int      taskId  = -1;

    public TimerManager(Plugin plugin, ScoreManager scoreManager) {
        this.plugin       = plugin;
        this.scoreManager = scoreManager;
    }

    // ── Run lifecycle ─────────────────────────────────────────────────────────

    /**
     * Start the dungeon run: initialise {@link RunState} and kick off the
     * repeating 1-second tick.
     *
     * Called by {@link com.ryan.dungeoncrawler.DungeonCommandExecutor} after
     * {@link ScoreManager#startSession} has already registered all players.
     */
    public void startRun() {
        if (taskId != -1) stopRun(); // safety: cancel any lingering task
        state = new RunState(1, SECONDS_PER_LEVEL, 1);
        broadcastLevelStart(state.getLevel());
        startTimerLoop();
    }

    /** Cancel the repeating task and clear state (safe to call when idle). */
    public void stopRun() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        state = null;
    }

    /** @return {@code true} while the timer task is running. */
    public boolean isRunning() {
        return taskId != -1 && state != null;
    }

    /** @return current {@link RunState}, or {@code null} if not running. */
    public RunState getState() { return state; }

    // ── Admin shortcut ────────────────────────────────────────────────────────

    /**
     * Immediately complete the current level with {@code secondsRemaining} banked.
     *
     * Used by /mgstageclear — mirrors the natural time-expiry path but lets an
     * admin specify how many seconds were left on the clock.
     *
     * @return {@code false} if the timer is not running or all levels are done.
     */
    public boolean clearCurrentLevel(int secondsRemaining) {
        if (!isRunning()) return false;
        if (state.getLevel() > TOTAL_LEVELS) return false;
        advanceLevel(secondsRemaining);
        return true;
    }

    // ── Internal timer loop ───────────────────────────────────────────────────

    private void startTimerLoop() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            state.tick();

            // Broadcast remaining time every 10 s and in the final 10 s.
            int t = state.getTimeRemaining();
            if (t % 10 == 0 || t <= 10) {
                Bukkit.broadcast(Component.text(
                        "⏱ Level " + state.getLevel() + " — " + t + "s remaining",
                        NamedTextColor.AQUA));
            }

            if (state.getTimeRemaining() <= 0) {
                // Time expired: bank 0 seconds and advance.
                advanceLevel(0);
            }

        }, 20L, 20L); // delay 1 s, period 1 s
    }

    /**
     * Bank remaining seconds in the score session, advance the {@link RunState},
     * and either start the next level or end the run when all levels are done.
     *
     * This is the single convergence point for both natural expiry and manual clear.
     */
    private void advanceLevel(int secondsRemaining) {
        int finishedLevel = state.getLevel();

        // Bank the time bonus into the scoring session (no-op if session ended).
        scoreManager.completeStage(secondsRemaining);

        Bukkit.broadcast(Component.text(
                "✔ Level " + finishedLevel + " complete! +" + secondsRemaining + " time-bonus pts.",
                NamedTextColor.GREEN));

        if (finishedLevel >= TOTAL_LEVELS) {
            // All levels done — stop timer and signal the score session to end.
            Bukkit.broadcast(Component.text(
                    "🏆 All levels complete! Tallying final scores...",
                    NamedTextColor.GOLD));
            stopRun();

            // Auto-finish the scoring session if still active.
            if (scoreManager.isSessionActive()) {
                scoreManager.tallyTreasuresFromInventories();
                com.ryan.dungeoncrawler.util.MessageUtil.broadcastFinalResults(
                        (JavaPlugin) plugin, scoreManager.getSession());
                scoreManager.clearSession();
            }
            return;
        }

        // Advance to the next level.
        state.nextLevel(SECONDS_PER_LEVEL);
        DifficultyStats stats = difficultyManager.getStatsForLevel(state.getLevel());

        broadcastLevelStart(state.getLevel());

        spawnMobsForLevel(stats);
    }

    // ── Mob spawning ──────────────────────────────────────────────────────────

    private void spawnMobsForLevel(DifficultyStats stats) {
        // TODO: replace with your dungeon room spawn location.
        if (Bukkit.getWorld("world") == null) return;
        Location spawn = Bukkit.getWorld("world").getSpawnLocation();

        for (int i = 0; i < stats.getMobCount(); i++) {
            Zombie z = spawn.getWorld().spawn(spawn, Zombie.class);
            z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(stats.getMobHealth());
            z.setHealth(stats.getMobHealth());
            z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(stats.getMobDamage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastLevelStart(int level) {
        Bukkit.broadcast(Component.text(
                "⚔ Level " + level + " of " + TOTAL_LEVELS + " has begun! "
                + SECONDS_PER_LEVEL + "s on the clock.",
                NamedTextColor.YELLOW));
    }
}
