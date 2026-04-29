package com.ryan.dungeoncrawler.model;

import java.util.*;

public class GameSession {

    public static final int TOTAL_STAGES = 3;

    private final boolean singleplayer;

    private final Map<UUID, PlayerScore> scores =
            new LinkedHashMap<>();

    private final int[] stageSeconds =
            new int[TOTAL_STAGES];

    private int currentStage = 0;

    public GameSession(boolean singleplayer) {
        this.singleplayer = singleplayer;
    }

    /* ====================================================== */
    /* PLAYERS                                                */
    /* ====================================================== */

    public void addPlayer(UUID id, String name) {
        scores.putIfAbsent(id, new PlayerScore(id, name));
    }

    public boolean hasPlayer(UUID id) {
        return scores.containsKey(id);
    }

    public PlayerScore getScore(UUID id) {
        return scores.get(id);
    }

    public Collection<PlayerScore> getAllScores() {
        return scores.values();
    }

    public Set<UUID> getPlayerIds() {
        return scores.keySet();
    }

    public int getPlayerCount() {
        return scores.size();
    }

    /* ====================================================== */
    /* STAGES                                                 */
    /* ====================================================== */

    public boolean completeCurrentStage(int secondsRemaining) {

        if (currentStage >= TOTAL_STAGES) {
            return false;
        }

        stageSeconds[currentStage] =
                Math.max(0, secondsRemaining);

        currentStage++;

        return true;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public int getDisplayStage() {
        return currentStage + 1;
    }

    public boolean allStagesDone() {
        return currentStage >= TOTAL_STAGES;
    }

    public int getTotalTimeBonus() {

        int total = 0;

        for (int sec : stageSeconds) {
            total += sec;
        }

        return total;
    }

    /* ====================================================== */
    /* MODE                                                   */
    /* ====================================================== */

    public boolean isSingleplayer() {
        return singleplayer;
    }
}
