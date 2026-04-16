package com.ryan.dungeoncrawler.listeners;

import com.ryan.dungeoncrawler.dungeon.Room;
import com.ryan.dungeoncrawler.dungeon.RoomRegistry;
import com.ryan.dungeoncrawler.game.RoomManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Fires room-enter and room-tick logic when a player moves into a new chunk.
 *
 * Updated to work with the corrected {@link RoomRegistry} which now stores
 * {@link Room} objects. The {@link Room#toEncounter()} bridge is used to
 * hand a {@link com.ryan.dungeoncrawler.dungeon.RoomEncounter} to
 * {@link RoomManager}.
 */
public class RoomListener implements Listener {

    private final RoomManager manager;

    public RoomListener(RoomManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Only act on chunk-boundary crossings to avoid per-block spam.
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();
        Room   room   = RoomRegistry.getRoom(player.getLocation().getChunk());

        if (room != null) {
            var encounter = room.toEncounter();
            manager.enter(encounter, player);
            manager.tick(encounter);
        }
    }
}
