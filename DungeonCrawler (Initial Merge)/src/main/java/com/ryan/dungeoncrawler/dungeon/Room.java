package com.ryan.dungeoncrawler.dungeon;

import org.bukkit.Location;

import java.io.File;

/**
 * Represents a placed dungeon room in the world.
 *
 * Created by {@link DungeonGenerator} after a schematic is successfully pasted.
 * Registered in {@link RoomRegistry} and used by {@link com.ryan.dungeoncrawler.listeners.RoomListener}
 * to trigger encounter logic when a player enters.
 *
 * {@link #toEncounter()} converts this structural record into a
 * {@link RoomEncounter} that the encounter/wave system can manage.
 */
public class Room {

    private final Location pasteLocation;
    private final int      width;
    private final int      length;
    private final File     schematicFile;
    private final boolean[] exits; // [N, E, S, W]

    /** Cached encounter — created lazily the first time toEncounter() is called. */
    private RoomEncounter encounter;

    public Room(Location pasteLocation, int width, int length,
                File schematicFile, boolean[] exits) {
        this.pasteLocation = pasteLocation;
        this.width         = width;
        this.length        = length;
        this.schematicFile = schematicFile;
        this.exits         = exits;
    }

    // ── Structural data ───────────────────────────────────────────────────────

    /** The world location where the schematic's origin was pasted. */
    public Location getPasteLocation() { return pasteLocation; }

    /**
     * Centre of the room in world coordinates — used as the teleport target
     * and encounter origin.
     */
    public Location getCenter() {
        return pasteLocation.clone().add(width / 2.0, 0, length / 2.0);
    }

    public int      getWidth()          { return width; }
    public int      getLength()         { return length; }
    public File     getSchematicFile()  { return schematicFile; }

    /**
     * Whether this room has a passage in the given direction after rotation.
     *
     * @param dir direction from {@link DungeonGenerator.Direction}
     */
    public boolean hasExit(DungeonGenerator.Direction dir) {
        return exits[dir.ordinal()];
    }

    // ── Encounter bridge ──────────────────────────────────────────────────────

    /**
     * Return (or lazily create) the {@link RoomEncounter} for this room.
     * The encounter uses this room's centre as its spawn origin.
     */
    public RoomEncounter toEncounter() {
        if (encounter == null) {
            encounter = new RoomEncounter(getCenter());
        }
        return encounter;
    }
}
