package com.minigame.scorer.model;

import java.util.UUID;

/**
 * Holds all scoring data for a single player during a game session.
 */
public class PlayerScore {

    private final UUID playerId;
    private final String playerName;

    private int killPoints    = 0;
    private int treasurePoints = 0;
    private int deaths        = 0;   // raw death count — penalty capped to 1 in singleplayer

    public PlayerScore(UUID playerId, String playerName) {
        this.playerId   = playerId;
        this.playerName = playerName;
    }

    // ── Kills ──────────────────────────────────────────────────────────────────

    /** Add the point value that corresponds to a mob's max health. */
    public void addKillPoints(int points) {
        killPoints += points;
    }

    // ── Treasures ─────────────────────────────────────────────────────────────

    public void addTreasurePoints(int points) {
        treasurePoints += points;
    }

    // ── Deaths ────────────────────────────────────────────────────────────────

    public void incrementDeaths() {
        deaths++;
    }

    // ── Score calculation ─────────────────────────────────────────────────────

    /**
     * Calculate the final score.
     *
     * @param timeBonus     Total extra seconds from all three stages.
     * @param isSingleplayer Whether the death penalty is capped at one occurrence.
     */
    public int calculateScore(int timeBonus, boolean isSingleplayer) {
        int effectiveDeaths = isSingleplayer ? Math.min(deaths, 1) : deaths;
        int deathPenalty    = effectiveDeaths * 20;
        return killPoints + treasurePoints + timeBonus - deathPenalty;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID   getPlayerId()       { return playerId; }
    public String getPlayerName()     { return playerName; }
    public int    getKillPoints()     { return killPoints; }
    public int    getTreasurePoints() { return treasurePoints; }
    public int    getDeaths()         { return deaths; }
}
