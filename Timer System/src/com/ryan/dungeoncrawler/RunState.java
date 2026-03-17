package com.ryan.dungeoncrawler;

public class RunState {

    private int level;
    private int timeRemaining;
    private int difficultyIndex;

    public RunState(int level, int timeRemaining, int difficultyIndex) {
        this.level = level;
        this.timeRemaining = timeRemaining;
        this.difficultyIndex = difficultyIndex;
    }

    public int getLevel() { return level; }
    public int getTimeRemaining() { return timeRemaining; }
    public int getDifficultyIndex() { return difficultyIndex; }

    public void tick() { timeRemaining--; }

    public void nextLevel(int newTime) {
        level++;
        difficultyIndex++;
        timeRemaining = newTime;
    }
}