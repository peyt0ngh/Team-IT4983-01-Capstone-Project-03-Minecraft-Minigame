package com.ryan.dungeonexplorers.managers;

import com.ryan.dungeonexplorers.model.GameSession;
import com.ryan.dungeonexplorers.util.MessageUtil;
import com.ryan.dungeonexplorers.util.SchematicUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class TimerManager {

    public static final int TOTAL_LEVELS = 3;

    // How many blocks to set AIR per tick during the arena clear.
    // 50 000 keeps each tick well under 50 ms even on modest hardware.
    private static final int BLOCKS_PER_TICK = 50_000;
    private static final int ARENA_RADIUS = 150;
    // Y ceiling relative to pasteLocation.getBlockY(). paste=-60, so max scanned Y = -60+20 = -40.
    private static final int ARENA_HEIGHT = 20;

    // Exact bounding box used to locate the lime-concrete spawn marker.
    // Adjust these if the dungeon layout ever changes.
    private static final int SPAWN_SCAN_MIN_X = 999;
    private static final int SPAWN_SCAN_MIN_Y = -62;
    private static final int SPAWN_SCAN_MIN_Z = -140;
    private static final int SPAWN_SCAN_MAX_X = 1139;
    private static final int SPAWN_SCAN_MAX_Y = -60;
    private static final int SPAWN_SCAN_MAX_Z = 2;

    // Loading room is pasted 50 blocks above the dungeon (dungeon y=-60, loading y=-10).
    private static final int LOADING_ROOM_Y_OFFSET = 50;

    private final JavaPlugin plugin;
    private final ScoreManager scoreManager;

    private int currentLevel = 0;
    private int timeRemaining = 0;
    private int taskId = -1;

    private final World world;
    private final Location pasteLocation;
    private final Location loadingRoomPasteLocation;
    private final Location lobbySpawn;
    private final Location loadingRoomSpawn;
    private final File loadingRoomFile;

    private final Set<UUID> waitingPlayers = new HashSet<>();

    /** Cached each time a stage loads; used by RespawnListener to send dead players back. */
    private Location currentSpawnLocation = null;

    public TimerManager(JavaPlugin plugin, ScoreManager scoreManager) {
        this.plugin = plugin;
        this.scoreManager = scoreManager;

        this.world = Bukkit.getWorld("world");
        this.pasteLocation = new Location(world, 1000, -60, 0);
        this.loadingRoomPasteLocation = new Location(world,
                pasteLocation.getX(),
                pasteLocation.getY() + LOADING_ROOM_Y_OFFSET,
                pasteLocation.getZ());
        this.lobbySpawn = new Location(world, -8, -60, 2);
        // One block above the coal block marker at 1002 -10 -4.
        this.loadingRoomSpawn = new Location(world, 1002.5, -9, -3.5);
        this.loadingRoomFile = new File(plugin.getDataFolder(), "maps/other/loading.schem");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void startRun() {
        stopRun();

        currentLevel = 1;
        loadStage(currentLevel);
        startTimer(stageSeconds(currentLevel));
    }

    public void stopRun() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isRunning() {
        return taskId != -1;
    }

    public boolean clearCurrentLevelWithCurrentTime() {
        return clearCurrentLevel(timeRemaining);
    }

    public boolean clearCurrentLevel(int secondsRemaining) {
        if (!isRunning()) return false;

        stopRun();
        scoreManager.completeStage(secondsRemaining);

        Bukkit.broadcast(Component.text(
                "✔ Stage " + currentLevel + " cleared!",
                NamedTextColor.GREEN
        ));

        if (currentLevel >= TOTAL_LEVELS) {
            finishRun();
            return true;
        }

        // Kill all remaining mobs before loading the next stage.
        killArenaMobs();

        currentLevel++;
        loadStage(currentLevel);
        startTimer(stageSeconds(currentLevel));

        return true;
    }

    public int getCurrentLevel() { return currentLevel; }
    public int getTimeRemaining() { return timeRemaining; }
    public Location getCurrentSpawnLocation() { return currentSpawnLocation; }

    public void sendToWaitingArea(Player player) {
        waitingPlayers.add(player.getUniqueId());

        Location waiting = new Location(world, 1200, -55, 0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.spigot().respawn();
            player.teleport(waiting);
            player.sendMessage("§eYou are out for this stage. You will rejoin next stage.");
        }, 2L);
    }

    // -------------------------------------------------------------------------
    // Timer
    // -------------------------------------------------------------------------

    private void startTimer(int seconds) {
        timeRemaining = seconds;

        Bukkit.broadcast(Component.text(
                "⚔ Stage " + currentLevel + " begins! Time: " + format(seconds),
                NamedTextColor.YELLOW
        ));

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            timeRemaining--;

            if (timeRemaining % 60 == 0 ||
                    (timeRemaining <= 30 && timeRemaining % 10 == 0) ||
                    timeRemaining <= 5) {

                Bukkit.broadcast(Component.text(
                        "⏱ " + format(timeRemaining) + " remaining",
                        NamedTextColor.AQUA
                ));
            }

            if (timeRemaining <= 0) {
                failRun();
            }

        }, 20L, 20L);
    }

    // -------------------------------------------------------------------------
    // Stage loading  (all Bukkit calls stay on the main thread)
    // -------------------------------------------------------------------------

    private void loadStage(int stage) {
        File folder = new File(plugin.getDataFolder(), "maps/stage" + stage);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] schems = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".schem")
                        || name.toLowerCase().endsWith(".schematic"));

        if (schems == null || schems.length == 0) {
            plugin.getLogger().warning("No schematics found in " + folder.getPath());
            return;
        }

        File chosen = schems[new Random().nextInt(schems.length)];

        killArenaMobs();

        // Paste the loading room, then wait 1 tick for WorldEdit to finish writing
        // chunks before teleporting players into it.
        pasteLoadingRoom();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleportPlayersToLoadingRoom();

            // Remove entities and begin the arena clear only after players are safely
            // inside the loading room.
            clearEntities();

            // Drain block clears across ticks so no single tick freezes the server.
            // The callback fires on the main thread once the last batch completes,
            // so pasteSchematic and all marker scanning remain fully thread-safe.
            clearBlocksBatched(() -> {
                SchematicUtil.pasteSchematic(chosen, pasteLocation, 0);

                Bukkit.broadcast(Component.text(
                        "Loaded map: " + chosen.getName(),
                        NamedTextColor.GRAY
                ));

                // One tick after paste so WorldEdit/FAWE finishes writing chunks
                // before we scan for markers.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    spawnMobsFromMarkers(stage, chosen.getName());
                    teleportPlayersToSpawn();
                }, 20L);
            });
        }, 1L);
    }

    /**
     * Removes all non-player entities inside the arena bounds.
     * Must be called on the main thread.
     */
    private void clearEntities() {
        if (world == null) return;

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - ARENA_RADIUS;
        int maxX = baseX + ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;
        int minZ = baseZ - ARENA_RADIUS;
        int maxZ = baseZ + ARENA_RADIUS;

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();
            if (loc.getX() >= minX && loc.getX() <= maxX &&
                    loc.getY() >= minY && loc.getY() <= maxY &&
                    loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                entity.remove();
            }
        }
    }

    /**
     * Clears all arena blocks by processing BLOCKS_PER_TICK positions per tick,
     * keeping the main thread responsive. Calls {@code onFinished} once done.
     *
     * Everything stays on the main thread — moving world access off-thread caused
     * the dungeon not to generate and markers not to be found, because Bukkit's
     * world API is not thread-safe.
     */
    private void clearBlocksBatched(Runnable onFinished) {
        if (world == null) {
            onFinished.run();
            return;
        }

        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int minX = baseX - ARENA_RADIUS;
        int maxX = baseX + ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;
        int minZ = baseZ - ARENA_RADIUS;
        int maxZ = baseZ + ARENA_RADIUS;

        int totalY = maxY - minY + 1;
        int totalZ = maxZ - minZ + 1;
        int totalBlocks = (maxX - minX + 1) * totalY * totalZ;

        int[] cursor = {0};
        int[] clearTaskId = {-1};

        clearTaskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int processed = 0;

            while (cursor[0] < totalBlocks && processed < BLOCKS_PER_TICK) {
                int idx = cursor[0];

                // Decode flat index back to (x, y, z).
                int lx = idx / (totalY * totalZ);
                int remainder = idx % (totalY * totalZ);
                int ly = remainder / totalZ;
                int lz = remainder % totalZ;

                // false = skip physics/lighting updates during bulk fill, much faster.
                world.getBlockAt(minX + lx, minY + ly, minZ + lz)
                        .setType(Material.AIR, false);

                cursor[0]++;
                processed++;
            }

            if (cursor[0] >= totalBlocks) {
                Bukkit.getScheduler().cancelTask(clearTaskId[0]);
                onFinished.run();
            }

        }, 0L, 1L);
    }

    // -------------------------------------------------------------------------
    // Marker scanning  (all on main thread — world reads are only safe here)
    // -------------------------------------------------------------------------

    /**
     * Scans for red concrete mob-spawn markers, spawns the mobs, then removes
     * the marker blocks.
     */
    private void spawnMobsFromMarkers(int stage, String schematicName) {
        int baseX = pasteLocation.getBlockX();
        int baseY = pasteLocation.getBlockY();
        int baseZ = pasteLocation.getBlockZ();

        int radius = ARENA_RADIUS;
        int minY = baseY;
        int maxY = baseY + ARENA_HEIGHT;

        String mapName = schematicName.toLowerCase();
        boolean isMansion4    = mapName.contains("mansion_4");
        boolean isAncientCity = mapName.contains("ancient_city");

        // Hard caps: mansion_4 gets exactly 2 Creakings total across all markers;
        //            ancient_city gets exactly 1 Warden total across all markers.
        int creakingsRemaining = 2;
        int wardensRemaining   = 1;

        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {

                    Block block = world.getBlockAt(x, y, z);

                    if (block.getType() == Material.RED_CONCRETE) {
                        Location spawnLoc = block.getLocation().add(0.5, 1.0, 0.5);
                        block.setType(Material.AIR); // always remove the marker

                        try {
                            if (isMansion4) {
                                if (creakingsRemaining > 0) {
                                    spawnMob(spawnLoc, EntityType.CREAKING);
                                    creakingsRemaining--;
                                }
                            } else if (isAncientCity) {
                                if (wardensRemaining > 0) {
                                    spawnMob(spawnLoc, EntityType.WARDEN);
                                    wardensRemaining--;
                                }
                            } else {
                                spawnMob(spawnLoc, chooseMobForStageAndMap(stage, schematicName));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(
                                    "Could not spawn mob: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void teleportPlayersToSpawn() {
        Location spawn = findSpawnMarker();

        if (spawn == null) {
            plugin.getLogger().warning("Falling back to default spawn offset.");
            spawn = pasteLocation.clone().add(8, 2, 8);
        }

        currentSpawnLocation = spawn.clone(); // cache for RespawnListener

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!scoreManager.isSessionActive()) continue;
            if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) continue;

            player.teleport(spawn);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setFireTicks(0);
            // Regeneration II for 8 seconds (160 ticks).
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1, false, true));

            if (currentLevel == 1) {
                giveStarterGear(player);
            }
        }

        waitingPlayers.clear();
    }

    /**
     * Locates lime concrete spawn marker(s) placed in the schematic and returns
     * a safe two-block-tall standing position above them.
     *
     * FIX: The original while loop had two bugs that caused players to fall into
     * the void or spawn inside blocks:
     *   1. Missing parentheses meant operator precedence evaluated the second
     *      air-check outside the `raised < maxRaise` guard entirely.
     *   2. `raised` was never incremented, making the cap completely useless.
     */
    private Location findSpawnMarker() {
        List<Location> markers = new ArrayList<>();

        for (int x = SPAWN_SCAN_MIN_X; x <= SPAWN_SCAN_MAX_X; x++) {
            for (int y = SPAWN_SCAN_MIN_Y; y <= SPAWN_SCAN_MAX_Y; y++) {
                for (int z = SPAWN_SCAN_MIN_Z; z <= SPAWN_SCAN_MAX_Z; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() == Material.LIME_CONCRETE) {
                        markers.add(b.getLocation());
                    }
                }
            }
        }

        plugin.getLogger().info("Found " + markers.size() + " lime concrete spawn marker(s).");

        if (markers.isEmpty()) {
            plugin.getLogger().warning("No lime spawn marker found in scan box " +
                    "[" + SPAWN_SCAN_MIN_X + "," + SPAWN_SCAN_MIN_Y + "," + SPAWN_SCAN_MIN_Z + "] -> " +
                    "[" + SPAWN_SCAN_MAX_X + "," + SPAWN_SCAN_MAX_Y + "," + SPAWN_SCAN_MAX_Z + "]");
            return null;
        }

        // Pick the marker with the highest Y so the player lands on top of
        // the floor tile rather than averaging into a wall or ceiling.
        Location best = markers.get(0);
        for (Location loc : markers) {
            if (loc.getY() > best.getY()) best = loc;
        }

        Location spawn = new Location(world, best.getX() + 0.5, best.getY() + 1, best.getZ() + 0.5);

        int maxRaise = 10;
        int raised = 0;

        while (raised < maxRaise && (
                !spawn.getBlock().getType().isAir()
                        || !spawn.clone().add(0, 1, 0).getBlock().getType().isAir())) {
            spawn.add(0, 1, 0);
            raised++;
        }

        return spawn;
    }

    // -------------------------------------------------------------------------
    // Mob selection
    // -------------------------------------------------------------------------

    private void spawnMob(Location loc, EntityType type) {
        Entity spawned = world.spawnEntity(loc, type);
        if (spawned instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
    }

    private EntityType chooseMobForStageAndMap(int stage, String schematicName) {
        String name = schematicName.toLowerCase();

        return switch (stage) {
            case 1 -> chooseStageOneThemeMob(name);
            case 2 -> chooseStageTwoThemeMob(name);
            case 3 -> chooseStageThreeThemeMob(name);
            default -> randomDefaultMob();
        };
    }

    private EntityType randomDefaultMob() {
        return Math.random() < 0.5 ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    private EntityType chooseStageOneThemeMob(String name) {
        if (name.contains("stronghold"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.SPIDER : EntityType.CAVE_SPIDER);
        if (name.contains("temple"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.ZOMBIE : EntityType.HUSK);
        if (name.contains("trial"))
            // 50% default, 50% themed (25% breeze / 75% bogged within themed half)
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.25 ? EntityType.BREEZE : EntityType.BOGGED);
        return randomDefaultMob();
    }

    private EntityType chooseStageTwoThemeMob(String name) {
        // mansion_4 is handled separately in spawnMobsFromMarkers — check it first
        // so it doesn't fall into the generic mansion branch.
        if (name.contains("mansion_4"))
            return randomDefaultMob();
        if (name.contains("mansion"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.VINDICATOR : EntityType.PILLAGER);
        if (name.contains("fortress"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.WITHER_SKELETON : EntityType.BLAZE);
        // ancient_city is handled separately in spawnMobsFromMarkers
        if (name.contains("ancient_city"))
            return randomDefaultMob();
        return randomDefaultMob();
    }

    private EntityType chooseStageThreeThemeMob(String name) {
        if (name.contains("end_city"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.ENDERMAN : EntityType.ENDERMITE);
        if (name.contains("monument"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.DROWNED : EntityType.BOGGED);
        if (name.contains("bastion"))
            return Math.random() < 0.5 ? randomDefaultMob()
                    : (Math.random() < 0.5 ? EntityType.MAGMA_CUBE : EntityType.WITHER_SKELETON);
        return randomDefaultMob();
    }

    // -------------------------------------------------------------------------
    // Run end states
    // -------------------------------------------------------------------------

    private void finishRun() {
        stopRun();
        killArenaMobs();
        currentSpawnLocation = null;

        Bukkit.broadcast(Component.text("🏆 Dungeon Cleared!", NamedTextColor.GOLD));

        if (scoreManager.isSessionActive()) {
            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, scoreManager.getSession());
            scoreManager.clearSession();
        }

        teleportPlayersToLobby();
    }

    private void failRun() {
        stopRun();
        killArenaMobs();
        currentSpawnLocation = null;

        Bukkit.broadcast(Component.text("☠ Time expired! Dungeon failed.", NamedTextColor.DARK_RED));

        if (scoreManager.isSessionActive()) {
            GameSession session = scoreManager.getSession();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (session.hasPlayer(player.getUniqueId())) {
                    player.setHealth(0);
                }
            }

            scoreManager.tallyTreasuresFromInventories();
            MessageUtil.broadcastFinalResults(plugin, session);
            scoreManager.clearSession();
        }

        // Send surviving players back to the lobby after death ticks resolve.
        Bukkit.getScheduler().runTaskLater(plugin, this::teleportPlayersToLobby, 2L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int stageSeconds(int level) {
        return switch (level) {
            case 1 -> 240;
            case 2 -> 360;
            case 3 -> 480;
            default -> 240;
        };
    }

    /**
     * Gives the player a nearly-broken wooden sword, leather armour set, and
     * shield at the start of stage 1. Clears their inventory first so they
     * begin each run on equal footing.
     */
    private void giveStarterGear(Player player) {
        player.getInventory().clear();

        player.getInventory().setItemInMainHand(damagedItem(Material.WOODEN_SWORD, 0.85));
        player.getInventory().setItemInOffHand(damagedItem(Material.SHIELD, 0.80));
        player.getInventory().setHelmet(damagedItem(Material.LEATHER_HELMET, 0.80));
        player.getInventory().setChestplate(damagedItem(Material.LEATHER_CHESTPLATE, 0.80));
        player.getInventory().setLeggings(damagedItem(Material.LEATHER_LEGGINGS, 0.80));
        player.getInventory().setBoots(damagedItem(Material.LEATHER_BOOTS, 0.80));
    }

    /**
     * Creates an ItemStack with its durability reduced to {@code damageFraction}
     * of max (e.g. 0.85 = 85% damaged, leaving 15% durability remaining).
     */
    private ItemStack damagedItem(Material material, double damageFraction) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int maxDurability = material.getMaxDurability();
            damageable.setDamage((int) (maxDurability * damageFraction));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String format(int total) {
        if (total < 0) total = 0;
        int m = total / 60;
        int s = total % 60;
        return m + ":" + String.format("%02d", s);
    }

    // -------------------------------------------------------------------------
    // Loading room & lobby teleports
    // -------------------------------------------------------------------------

    /**
     * Pastes the loading room schematic above the dungeon so players have
     * somewhere safe to stand while the arena is being cleared and rebuilt.
     */
    private void pasteLoadingRoom() {
        if (!loadingRoomFile.exists()) {
            plugin.getLogger().warning("Loading room schematic not found: " + loadingRoomFile.getPath());
            return;
        }
        SchematicUtil.pasteSchematic(loadingRoomFile, loadingRoomPasteLocation, 0);
    }

    /**
     * Teleports all active session players to the coal block spawn marker
     * inside the loading room.
     */
    private void teleportPlayersToLoadingRoom() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!scoreManager.isSessionActive()) continue;
            if (!scoreManager.getSession().hasPlayer(player.getUniqueId())) continue;
            player.teleport(loadingRoomSpawn);
        }
    }

    /**
     * Teleports all session players (including waiting ones) to the lobby spawn
     * at the end of a run (win or loss).
     */
    private void teleportPlayersToLobby() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobbySpawn);
        }
        waitingPlayers.clear();
    }

    // -------------------------------------------------------------------------
    // Mob cleanup
    // -------------------------------------------------------------------------

    /**
     * Kills every non-player living entity in the world.
     * Called between stages and at the end of a run.
     */
    /**
     * Force-loads every chunk in the arena bounding box, then removes all
     * non-player living entities. Chunks are force-loaded so they are ticked
     * even when no players are nearby, ensuring mobs are reachable.
     * Force-load is then lifted so the chunks can unload naturally later.
     */
    private void killArenaMobs() {
        if (world == null) return;

        int minChunkX = (pasteLocation.getBlockX() - ARENA_RADIUS) >> 4;
        int maxChunkX = (pasteLocation.getBlockX() + ARENA_RADIUS) >> 4;
        int minChunkZ = (pasteLocation.getBlockZ() - ARENA_RADIUS) >> 4;
        int maxChunkZ = (pasteLocation.getBlockZ() + ARENA_RADIUS) >> 4;

        // Force-load all arena chunks so entities in them are accessible.
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }

        // Remove every non-player living entity in those chunks.
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Player) continue;
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity.isDead()) continue;
                    entity.remove();
                }
            }
        }

        // Release force-load so chunks can unload normally when empty.
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.setChunkForceLoaded(cx, cz, false);
            }
        }
    }
}
