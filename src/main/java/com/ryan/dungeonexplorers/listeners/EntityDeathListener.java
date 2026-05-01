package com.ryan.dungeoncrawler.listeners;

import com.ryan.dungeoncrawler.managers.ScoreManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listens for entity deaths to:
 *  - Award kill points to the player responsible.
 *  - Roll for a treasure drop on the slain mob.
 */
public class EntityDeathListener implements Listener {

    private final ScoreManager scoreManager;

    public EntityDeathListener(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!scoreManager.isSessionActive()) return;

        LivingEntity entity = event.getEntity();

        // Only score kills on non-player living entities.
        if (entity instanceof Player) return;

        Player killer = entity.getKiller();
        if (killer == null) return; // not killed by a player

        // Award kill points.
        scoreManager.recordKill(killer, entity);

        // Possibly drop a treasure at the mob's location.
        scoreManager.handleMobTreasureDrop(entity);
    }
}
