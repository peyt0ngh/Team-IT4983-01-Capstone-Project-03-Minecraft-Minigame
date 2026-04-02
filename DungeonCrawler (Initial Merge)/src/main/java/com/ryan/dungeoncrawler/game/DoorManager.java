package com.ryan.dungeoncrawler.game;

import org.bukkit.*;
import org.bukkit.block.Block;
import com.ryan.dungeoncrawler.dungeon.RoomEncounter;

public class DoorManager {

    public static void lock(RoomEncounter room) {

        for (Block door : room.getDoors()) {

            door.setType(Material.OAK_FENCE);
            door.getRelative(0,1,0).setType(Material.BARRIER);

            door.getWorld().playSound(door.getLocation(),
                    Sound.BLOCK_IRON_DOOR_CLOSE,1,1);
        }
    }

    public static void unlock(RoomEncounter room) {

        for (Block door : room.getDoors()) {

            door.setType(Material.AIR);
            door.getRelative(0,1,0).setType(Material.AIR);

            door.getWorld().playSound(door.getLocation(),
                    Sound.BLOCK_IRON_DOOR_OPEN,1,1);
        }
    }
}