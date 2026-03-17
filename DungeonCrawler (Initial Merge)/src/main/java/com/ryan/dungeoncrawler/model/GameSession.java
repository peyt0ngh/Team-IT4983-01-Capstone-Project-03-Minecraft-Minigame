package com.ryan.dungeoncrawler.model;

import java.util.*;

/**
 * Represents one active dungeon run session.
 * Tracks player scores, stages, and game mode.
 *
 * TOTAL_STAGES is intentionally equal to TimerManager.TOTAL_LEVELS (both 3)
 * so that every timer level advance maps exactly to one stage completion.
 */
public class GameSession {

    /** Must equal {@link com.ryan.dungeoncrawler.managers.TimerManager#TOTAL_LEVELS}. */
    public static final int TOTAL_STAGES = 3;

    private final boolean singleplayer;
    private final Map<UUID, PlayerScore> scores = new LinkedHashMap<>();

    /** Seconds banked from each completed stage (index 0-2). */
    private final int[] stageSeconds = new int[TOTAL_STAGES];
    private int currentStage = 0; // 0-based internally; == TOTAL_STAGES means all done

    public GameSession(boolean singleplayer) {
        this.singleplayer = singleplayer;
    }

    // ── Player management ─────────────────────────────────────────────────────

    public void addPlayer(UUID id, String name) {
        scores.putIfAbsent(id, new PlayerScore(id, name));
    }

    public PlayerScore getScore(UUID id) {
        return scores.get(id);
    }

    public Collection<PlayerScore> getAllScores() {
        return scores.values();
    }

    // ── Stages ────────────────────────────────────────────────────────────────

    /**
     * Record the remaining seconds for the current stage and advance to the next.
     *
     * @param secondsRemaining seconds left on the clock when the stage ended.
     * @return false if all stages are already complete.
     */
    public boolean completeCurrentStage(int secondsRemaining) {
        if (currentStage >= TOTAL_STAGES) return false;
        stageSeconds[currentStage] = Math.max(0, secondsRemaining);
        currentStage++;
        return true;
    }

    /** Total time bonus (seconds) across all completed stages. */
    public int getTotalTimeBonus() {
        int total = 0;
        for (int s : stageSeconds) total += s;
        return total;
    }

    /** 0-based stage index; use getCurrentStage()+1 for display. */
    public int getCurrentStage()   { return currentStage; }
    public boolean allStagesDone() { return currentStage >= TOTAL_STAGES; }
    public boolean isSingleplayer() { return singleplayer; }
}
