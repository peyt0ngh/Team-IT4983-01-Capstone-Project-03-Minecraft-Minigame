package com.ryan.dungeoncrawler.dungeon;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public class RoomEncounter {

    private final Location center;
    private final List<Block> doors = new ArrayList<>();

    private boolean cleared = false;
    private boolean active = false;

    public RoomEncounter(Location center) {
        this.center = center;
    }

    public Location getCenter() { return center; }

    public List<Block> getDoors() { return doors; }

    public void addDoor(Block block) { doors.add(block); }

    public boolean isCleared() { return cleared; }
    public void setCleared(boolean cleared) { this.cleared = cleared; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}