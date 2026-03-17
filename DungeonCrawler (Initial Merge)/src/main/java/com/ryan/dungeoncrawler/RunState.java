package com.ryan.dungeoncrawler;

/**
 * Mutable state for the current dungeon run.
 *
 * Tracks:
 *  - level          : current dungeon level (1-based, max = TimerManager.TOTAL_LEVELS)
 *  - timeRemaining  : seconds left on the clock for this level
 *  - difficultyIndex: passed to DifficultyManager to scale mob stats
 *
 * difficultyIndex mirrors level so they advance together; kept separate
 * to allow future divergence (e.g. difficulty presets or bonus levels).
 */
public class RunState {

    private int level;
    private int timeRemaining;
    private int difficultyIndex;

    public RunState(int level, int timeRemaining, int difficultyIndex) {
        this.level           = level;
        this.timeRemaining   = timeRemaining;
        this.difficultyIndex = difficultyIndex;
    }

    /** Decrement the clock by one second. */
    public void tick() { timeRemaining--; }

    /**
     * Advance to the next level and reset the countdown.
     *
     * @param newTime seconds for the next level (normally TimerManager.SECONDS_PER_LEVEL).
     */
    public void nextLevel(int newTime) {
        level++;
        difficultyIndex++;
        timeRemaining = newTime;
    }

    public int getLevel()           { return level; }
    public int getTimeRemaining()   { return timeRemaining; }
    public int getDifficultyIndex() { return difficultyIndex; }
}
