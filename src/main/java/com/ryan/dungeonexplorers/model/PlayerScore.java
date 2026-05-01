package com.ryan.dungeoncrawler.model;

import java.util.UUID;

/**
 * Holds all scoring data for a single player during a game session.
 */
public class PlayerScore {

    private final UUID   playerId;
    private final String playerName;

    private int killPoints     = 0;
    private int treasurePoints = 0;
    private int deaths         = 0;

    public PlayerScore(UUID playerId, String playerName) {
        this.playerId   = playerId;
        this.playerName = playerName;
    }

    public void addKillPoints(int points)     { killPoints     += points; }
    public void addTreasurePoints(int points) { treasurePoints += points; }
    public void incrementDeaths()             { deaths++; }

    /**
     * Calculate the final score.
     *
     * @param timeBonus      Total extra seconds banked from all stages.
     * @param isSingleplayer Whether the death penalty is capped at one occurrence.
     */
    public int calculateScore(int timeBonus, boolean isSingleplayer) {
        int effectiveDeaths = isSingleplayer ? Math.min(deaths, 1) : deaths;
        int deathPenalty    = effectiveDeaths * 20;
        return killPoints + treasurePoints + timeBonus - deathPenalty;
    }

    public UUID   getPlayerId()       { return playerId; }
    public String getPlayerName()     { return playerName; }
    public int    getKillPoints()     { return killPoints; }
    public int    getTreasurePoints() { return treasurePoints; }
    public int    getDeaths()         { return deaths; }
}
