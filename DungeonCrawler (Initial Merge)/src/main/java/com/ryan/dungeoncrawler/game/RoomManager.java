package com.ryan.dungeoncrawler.game;

import com.ryan.dungeoncrawler.dungeon.RoomEncounter;
import com.ryan.dungeoncrawler.mobs.WaveRoomManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class RoomManager {

    private final WaveRoomManager waves;

    public RoomManager(Plugin plugin) {
        this.waves = new WaveRoomManager(plugin);
    }

    public void enter(RoomEncounter room, Player player) {

        if (room.isCleared() || room.isActive()) return;

        room.setActive(true);

        DoorManager.lock(room);
        waves.start(room, List.of(player));
    }

    public void tick(RoomEncounter room) {

        if (room.isCleared()) {
            DoorManager.unlock(room);
            room.setActive(false);
        }
    }
}