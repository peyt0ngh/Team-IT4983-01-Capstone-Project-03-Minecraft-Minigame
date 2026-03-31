package your.plugin.difficulty;

import com.ryan.dungeoncrawler.DifficultyManager;
import com.ryan.dungeoncrawler.DifficultyStats;

/**
 * Wraps DifficultyManager and adds wave scaling logic.
 */
public class DifficultyAdapter {

    private final DifficultyManager manager;

    public DifficultyAdapter(DifficultyManager manager) {
        this.manager = manager;
    }

    public DifficultyStats getStats(int level) {
        return manager.getStatsForLevel(level);
    }

    /**
     * Determines additional waves based on stage.
     */
    public int getWaveBonus(int level) {
        int waveBonus = (level - 1) / 2;
        return Math.min(waveBonus, 3);
    }
}