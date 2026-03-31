package your.plugin.mobs;

import org.bukkit.Location;

/**
 * Stores information about a dungeon room.
 */
public class RoomEncounter {

    private final String roomId;
    private final Location center;
    private final RoomCategory category;
    private boolean cleared = false;

    public RoomEncounter(String roomId, Location center, RoomCategory category) {
        this.roomId = roomId;
        this.center = center;
        this.category = category;
    }

    public String getRoomId() { return roomId; }
    public Location getCenter() { return center; }
    public RoomCategory getCategory() { return category; }

    public boolean isCleared() { return cleared; }
    public void setCleared(boolean cleared) { this.cleared = cleared; }
}