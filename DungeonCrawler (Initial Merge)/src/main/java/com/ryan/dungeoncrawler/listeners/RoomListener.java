package com.ryan.dungeoncrawler.listeners;

import com.ryan.dungeoncrawler.dungeon.RoomRegistry;
import com.ryan.dungeoncrawler.game.RoomManager;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;

public class RoomListener implements Listener {

    private final RoomManager manager;

    public RoomListener(RoomManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {

        Player p = e.getPlayer();

        var room = RoomRegistry.getRoom(p.getLocation().getChunk());

        if (room != null) {
            manager.enter(room, p);
            manager.tick(room);
        }
    }
}