package com.minigame.scorer.model;

import java.util.*;

/**
 * Represents one active minigame session.
 * Tracks player scores, stages, and game mode.
 */
public class GameSession {

    public static final int TOTAL_STAGES = 3;

    private final boolean singleplayer;
    private final Map<UUID, PlayerScore> scores = new LinkedHashMap<>();

    /** Seconds banked from each completed stage (index 0-2). */
    private final int[] stageSeconds = new int[TOTAL_STAGES];
    private int currentStage = 0; // 0-based; == TOTAL_STAGES means all stages done

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

    public int getCurrentStage()   { return currentStage; }   // 1-based for display
    public boolean allStagesDone() { return currentStage >= TOTAL_STAGES; }

    // ── Misc ──────────────────────────────────────────────────────────────────

    public boolean isSingleplayer() { return singleplayer; }
}
