package com.ryan.dungeoncrawler.managers;

import com.ryan.dungeoncrawler.DifficultyManager;
import com.ryan.dungeoncrawler.DifficultyStats;
import com.ryan.dungeoncrawler.RunState;
import com.ryan.dungeoncrawler.model.GameSession;
import com.ryan.dungeoncrawler.mobs.EncounterConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages per-level countdown timers, difficulty-scaled mob spawning, and
 * game-over handling when a level's timer expires.
 *
 * <p><b>Win condition:</b> players must use /mgstageclear (or the dungeon's
 * stage-completion trigger) to advance through all three stages before each
 * stage's clock hits zero. Clearing stage 3 wins the run.</p>
 *
 * <p><b>Lose condition:</b> if the timer for <em>any</em> stage reaches zero
 * before the stage is cleared, every online player is killed, all dungeon mobs
 * are removed, and the session ends with a "defeat" broadcast.</p>
 *
 * Timer budgets per level (from {@link EncounterConfig}):
 * <ul>
 *   <li>Level 1 — 4 minutes (240 s)</li>
 *   <li>Level 2 — 6 minutes (360 s)</li>
 *   <li>Level 3 — 8 minutes (480 s)</li>
 * </ul>
 *
 * Level count matches {@link GameSession#TOTAL_STAGES} (both 3).
 */
public class TimerManager {

    /** Total number of dungeon levels — must equal GameSession.TOTAL_STAGES. */
    public static final int TOTAL_LEVELS = GameSession.TOTAL_STAGES; // 3

    /**
     * Seconds per level — kept for API compatibility; use
     * {@link EncounterConfig#secondsForLevel(int)} for the actual per-level value.
     *
     * @deprecated Prefer {@link EncounterConfig#secondsForLevel(int)}.
     */
    @Deprecated
    public static final int SECONDS_PER_LEVEL = EncounterConfig.SECONDS_LEVEL_1;

    private final Plugin          plugin;
    private final ScoreManager    scoreManager;
    private final DifficultyManager difficultyManager = new DifficultyManager();

    private RunState state  = null;
    private int      taskId = -1;

    public TimerManager(Plugin plugin, ScoreManager scoreManager) {
        this.plugin       = plugin;
        this.scoreManager = scoreManager;
    }

    // ── Run lifecycle ─────────────────────────────────────────────────────────

    /**
     * Start the dungeon run: initialise {@link RunState} at level 1 with its
     * configured seconds and kick off the repeating 1-second tick.
     */
    public void startRun() {
        if (taskId != -1) stopRun(); // cancel any lingering task
        int startSeconds = EncounterConfig.secondsForLevel(1);
        state = new RunState(1, startSeconds, 1);
        broadcastLevelStart(state.getLevel());
        spawnMobsForLevel(difficultyManager.getStatsForLevel(1), 1);
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
     * Immediately complete the current level, banking {@code secondsRemaining}.
     *
     * Used by /mgstageclear.
     *
     * @return {@code false} if the timer is not running or all levels are done.
     */
    public boolean clearCurrentLevel(int secondsRemaining) {
        if (!isRunning()) return false;
        if (state.getLevel() > TOTAL_LEVELS) return false;
        killDungeonMobs();
        advanceLevel(secondsRemaining);
        return true;
    }

    // ── Internal timer loop ───────────────────────────────────────────────────

    private void startTimerLoop() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            state.tick();
            int t = state.getTimeRemaining();

            // Broadcast at round multiples of 60 s, every 10 s under 60, and last 5 s.
            if (t % 60 == 0 || (t <= 60 && t % 10 == 0) || t <= 5) {
                Bukkit.broadcast(Component.text(
                        "⏱ Level " + state.getLevel() + " — " + formatTime(t) + " remaining",
                        NamedTextColor.AQUA));
            }

            if (t <= 0) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
                gameOver(state.getLevel());
            }

        }, 20L, 20L); // 1-second period
    }

    /**
     * Bank remaining seconds, advance the {@link RunState}, and either start
     * the next level or end the run when all levels are done.
     *
     * Single convergence point for natural expiry and manual clear.
     */
    private void advanceLevel(int secondsRemaining) {
        int finishedLevel = state.getLevel();

        // Bank the time bonus (no-op if the session already ended).
        scoreManager.completeStage(secondsRemaining);

        Bukkit.broadcast(Component.text(
                "✔ Level " + finishedLevel + " complete! +" + secondsRemaining + " time-bonus pts.",
                NamedTextColor.GREEN));

        if (finishedLevel >= TOTAL_LEVELS) {
            // All levels cleared in time — victory!
            Bukkit.broadcast(Component.text(
                    "⚔ DUNGEON CONQUERED! The party has escaped!", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text(
                    "Tallying final scores…", NamedTextColor.YELLOW));
            stopRun();

            if (scoreManager.isSessionActive()) {
                scoreManager.tallyTreasuresFromInventories();
                com.ryan.dungeoncrawler.util.MessageUtil.broadcastFinalResults(
                        (JavaPlugin) plugin, scoreManager.getSession());
                scoreManager.clearSession();
            }
            return;
        }

        // Move to the next level with its own configured time budget.
        int nextLevel     = finishedLevel + 1;
        int nextSeconds   = EncounterConfig.secondsForLevel(nextLevel);
        state.nextLevel(nextSeconds);

        DifficultyStats stats = difficultyManager.getStatsForLevel(nextLevel);
        broadcastLevelStart(nextLevel);
        spawnMobsForLevel(stats, nextLevel);
        startTimerLoop(); // restart the loop for the new level
    }

    // ── Game-over / victory ───────────────────────────────────────────────────

    /**
     * Called when a stage timer expires without being cleared.
     * Kills every online player, removes all dungeon mobs, ends the session,
     * and broadcasts the defeat message.
     *
     * @param failedLevel the level whose timer ran out
     */
    private void gameOver(int failedLevel) {
        // Announce defeat first so players see it before the death screen.
        Bukkit.broadcast(Component.text(
                "☠ DUNGEON FAILED — Time expired on Level " + failedLevel + "!",
                NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text(
                "The dungeon claims another party...",
                NamedTextColor.RED));

        // Kill all online players (recorded as deaths by PlayerDeathListener).
        killAllPlayers();

        // Remove remaining mobs so they don't linger after the session ends.
        killDungeonMobs();

        // Stop the timer and close the scoring session.
        stopRun();
        if (scoreManager.isSessionActive()) {
            scoreManager.tallyTreasuresFromInventories();
            com.ryan.dungeoncrawler.util.MessageUtil.broadcastFinalResults(
                    (JavaPlugin) plugin, scoreManager.getSession());
            scoreManager.clearSession();
        }
    }

    /**
     * Kill every online player with a void/environment-style death.
     * Each death fires {@link org.bukkit.event.entity.PlayerDeathEvent}, which
     * {@link com.ryan.dungeoncrawler.listeners.PlayerDeathListener} will catch
     * to record the death penalty — but because the session is still active at
     * this point the penalty is applied before {@link #gameOver} clears it.
     */
    private void killAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(
                    "☠ The dungeon has consumed you.", NamedTextColor.DARK_RED));
            player.setHealth(0); // triggers PlayerDeathEvent normally
        }
    }



    /**
     * Spawn difficulty-scaled mobs for the given level.
     * Mob types are drawn from a randomly chosen environment for that level.
     *
     * @param stats difficulty snapshot (provides mob count and health/damage)
     * @param level 1-based dungeon level
     */
    private void spawnMobsForLevel(DifficultyStats stats, int level) {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            plugin.getLogger().warning("World 'world' not found — skipping mob spawn.");
            return;
        }

        Location spawnBase = world.getSpawnLocation();
        EncounterConfig.Environment env = EncounterConfig.randomForLevel(level);

        Bukkit.broadcast(Component.text(
                "☠ Environment: " + env.name(), NamedTextColor.DARK_AQUA));

        int count = stats.getMobCount();
        for (int i = 0; i < count; i++) {
            // Alternate between primary and secondary mob types.
            org.bukkit.entity.EntityType type =
                    (i % 2 == 0) ? env.primary() : env.secondary();

            // Small random spread around spawn so mobs aren't stacked.
            double ox = (Math.random() - 0.5) * 6;
            double oz = (Math.random() - 0.5) * 6;
            Location loc = spawnBase.clone().add(ox, 0, oz);

            try {
                Entity entity = world.spawnEntity(loc, type);

                // Apply difficulty-scaled stats to LivingEntity mobs.
                if (entity instanceof LivingEntity living) {
                    applyStats(living, stats);
                }

                // Force medium size for slime variants.
                if (entity instanceof Slime slime) {
                    slime.setSize(2);
                }
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Could not spawn " + type + ": " + e.getMessage());
            }
        }
    }

    /**
     * Apply difficulty-scaled health and damage to a spawned mob.
     */
    private void applyStats(LivingEntity entity, DifficultyStats stats) {
        try {
            var maxHp = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHp != null) {
                maxHp.setBaseValue(stats.getMobHealth());
                entity.setHealth(stats.getMobHealth());
            }
            var atk = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (atk != null) {
                atk.setBaseValue(stats.getMobDamage());
            }
        } catch (Exception e) {
            // Some entity types don't have these attributes — ignore silently.
        }
    }

    /**
     * Kill every {@link Monster} and {@link Slime} currently alive in the
     * primary world.
     *
     * @return the number of entities killed
     */
    private int killDungeonMobs() {
        World world = Bukkit.getWorld("world");
        if (world == null) return 0;

        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Monster || entity instanceof Slime) {
                entity.remove();
                count++;
            }
        }
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastLevelStart(int level) {
        int seconds = EncounterConfig.secondsForLevel(level);
        Bukkit.broadcast(Component.text(
                "⚔ Level " + level + " of " + TOTAL_LEVELS + " has begun! "
                + formatTime(seconds) + " on the clock.",
                NamedTextColor.YELLOW));
    }

    /** Format seconds as M:SS for cleaner broadcasts. */
    private static String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return min + ":" + String.format("%02d", sec);
    }
}
