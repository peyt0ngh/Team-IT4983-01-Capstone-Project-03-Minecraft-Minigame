package com.ryan.dungeoncrawler.dungeon;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DungeonLayoutGenerator {

    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    public enum RoomType {
        START, END, HALL, CORNER, BRANCH, INTERSECTION
    }

    public static class LayoutRoom {
        public int x, z;
        public Set<Direction> connections;
        public RoomType type;

        public LayoutRoom(int x, int z) {
            this.x = x;
            this.z = z;
            this.connections = new HashSet<>();
        }
    }

    private final Random random = new Random();

    public List<LayoutRoom> generate(int size) {

        boolean[][] occupied = new boolean[size][size];

        List<Point> mainPath = generateMainPath(size, occupied);
        generateBranches(size, occupied, mainPath);

        return buildRooms(size, occupied, mainPath);
    }

    // ─────────────────────────────────────────────
    // MAIN PATH GENERATION
    // ─────────────────────────────────────────────

    private List<Point> generateMainPath(int size, boolean[][] occupied) {

        List<Point> path = new ArrayList<>();
        Set<Point> used = new HashSet<>();

        Point start = new Point(size / 2, size / 2);

        Point current = start;
        path.add(current);
        used.add(current);
        occupied[current.x][current.y] = true;

        int targetLength = (int) (size * size * 0.4);

        while (path.size() < targetLength) {

            List<Point> neighbors = getUnusedNeighbors(current, used, size);

            if (neighbors.isEmpty()) {
                current = path.get(random.nextInt(path.size()));
                continue;
            }

            Point next = neighbors.get(random.nextInt(neighbors.size()));

            path.add(next);
            used.add(next);
            occupied[next.x][next.y] = true;
            current = next;
        }

        return path;
    }

    // ─────────────────────────────────────────────
    // BRANCH GENERATION
    // ─────────────────────────────────────────────

    private void generateBranches(int size, boolean[][] occupied, List<Point> mainPath) {

        Set<Point> used = new HashSet<>(mainPath);

        for (Point p : mainPath) {

            if (random.nextDouble() < 0.4) {

                List<Point> neighbors = getUnusedNeighbors(p, used, size);

                if (!neighbors.isEmpty()) {
                    Point branchStart = neighbors.get(random.nextInt(neighbors.size()));
                    createBranch(branchStart, used, occupied, size);
                }
            }
        }
    }

    private void createBranch(Point start, Set<Point> used, boolean[][] occupied, int size) {

        Point current = start;
        int length = 1 + random.nextInt(3);

        for (int i = 0; i < length; i++) {

            used.add(current);
            occupied[current.x][current.y] = true;

            List<Point> neighbors = getUnusedNeighbors(current, used, size);

            if (neighbors.isEmpty()) break;

            current = neighbors.get(random.nextInt(neighbors.size()));
        }
    }

    // ─────────────────────────────────────────────
    // BUILD FINAL ROOMS
    // ─────────────────────────────────────────────

    private List<LayoutRoom> buildRooms(int size, boolean[][] occupied, List<Point> mainPath) {

        List<LayoutRoom> rooms = new ArrayList<>();

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {

                if (!occupied[x][z]) continue;

                LayoutRoom room = new LayoutRoom(x, z);

                // Check connections
                for (Direction dir : Direction.values()) {
                    int nx = x + dx(dir);
                    int nz = z + dz(dir);

                    if (inBounds(nx, nz, size) && occupied[nx][nz]) {
                        room.connections.add(dir);
                    }
                }

                rooms.add(room);
            }
        }

        // Assign types
        for (int i = 0; i < rooms.size(); i++) {

            LayoutRoom room = rooms.get(i);

            if (isSame(room, mainPath.get(0))) {
                room.type = RoomType.START;
                continue;
            }

            if (isSame(room, mainPath.get(mainPath.size() - 1))) {
                room.type = RoomType.END;
                continue;
            }

            int count = room.connections.size();

            if (count == 1) {
                room.type = RoomType.END;
            } else if (count == 2) {
                if (isStraight(room.connections)) {
                    room.type = RoomType.HALL;
                } else {
                    room.type = RoomType.CORNER;
                }
            } else if (count == 3) {
                room.type = RoomType.BRANCH;
            } else {
                room.type = RoomType.INTERSECTION;
            }
        }

        return rooms;
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private List<Point> getUnusedNeighbors(Point p, Set<Point> used, int size) {

        List<Point> neighbors = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            int nx = p.x + dx(dir);
            int nz = p.y + dz(dir);

            Point np = new Point(nx, nz);

            if (inBounds(nx, nz, size) && !used.contains(np)) {
                neighbors.add(np);
            }
        }

        return neighbors;
    }

    private boolean isStraight(Set<Direction> dirs) {
        return (dirs.contains(Direction.NORTH) && dirs.contains(Direction.SOUTH)) ||
               (dirs.contains(Direction.EAST) && dirs.contains(Direction.WEST));
    }

    private boolean isSame(LayoutRoom room, Point p) {
        return room.x == p.x && room.z == p.y;
    }

    private int dx(Direction d) {
        return switch (d) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    private int dz(Direction d) {
        return switch (d) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }

    private boolean inBounds(int x, int z, int size) {
        return x >= 0 && z >= 0 && x < size && z < size;
    }
}
