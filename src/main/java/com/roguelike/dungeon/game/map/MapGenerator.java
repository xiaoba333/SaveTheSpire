package com.roguelike.dungeon.game.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 创建可复现的基础关卡地图。 */
public final class MapGenerator {
    public static final int FLOOR_COUNT = 14;
    /** 房间在横向网格中的最大列数，用于居中摆放变宽/变窄的层。 */
    public static final int MAX_COLUMNS = 5;
    public static final int MIN_FLOOR_WIDTH = 2;
    public static final int MAX_FLOOR_WIDTH = 5;
    /** 每一章地图固定放置的精英房数量。 */
    public static final int ELITE_COUNT = 3;

    private record PlacedNode(int id, int floor, int column, MapNodeType type) {
        PlacedNode withType(MapNodeType newType) {
            return new PlacedNode(id, floor, column, newType);
        }
    }

    /**
     * 根据随机种子生成地图。同一个种子始终生成相同的地图，方便联调和测试。
     */
    public DungeonMap generate(long seed) {
        Random random = new Random(seed);
        int[] widths = chooseWidths(random);
        List<List<Integer>> columnsByFloor = assignColumns(widths);
        Map<Integer, Integer> eliteColumns = placeElites(random, columnsByFloor);

        int nextId = 0;
        List<List<PlacedNode>> floors = new ArrayList<>(FLOOR_COUNT);
        for (int floor = 0; floor < FLOOR_COUNT; floor++) {
            List<PlacedNode> row = new ArrayList<>();
            for (int column : columnsByFloor.get(floor)) {
                row.add(new PlacedNode(nextId++, floor, column, MapNodeType.BATTLE));
            }
            floors.add(row);
        }

        List<Map<Integer, List<Integer>>> outgoingByFloor = new ArrayList<>(FLOOR_COUNT);
        for (int floor = 0; floor < FLOOR_COUNT - 1; floor++) {
            outgoingByFloor.add(connect(floors.get(floor), floors.get(floor + 1), random));
        }
        outgoingByFloor.add(Map.of());

        assignTypes(floors, outgoingByFloor, eliteColumns, random);

        List<MapNode> nodes = new ArrayList<>();
        for (int floor = 0; floor < FLOOR_COUNT; floor++) {
            Map<Integer, List<Integer>> outgoing = outgoingByFloor.get(floor);
            for (PlacedNode placed : floors.get(floor)) {
                nodes.add(new MapNode(
                        placed.id(),
                        placed.floor(),
                        placed.column(),
                        placed.type(),
                        outgoing.getOrDefault(placed.id(), List.of())));
            }
        }
        return new DungeonMap(nodes);
    }

    /**
     * 入口 2～3 间，中间随机走宽到最多 5，Boss 前再收到 2～3。
     * 相邻层（不含 Boss）房间数最多相差 1。
     */
    static int[] chooseWidths(Random random) {
        int[] widths = new int[FLOOR_COUNT];
        int restFloor = FLOOR_COUNT - 2;
        widths[0] = MIN_FLOOR_WIDTH + random.nextInt(2);

        for (int floor = 1; floor < restFloor; floor++) {
            int prev = widths[floor - 1];
            int floorsUntilRest = restFloor - floor;
            int maxAllowed = Math.min(MAX_FLOOR_WIDTH, 3 + floorsUntilRest);
            int lo = Math.max(MIN_FLOOR_WIDTH, prev - 1);
            int hi = Math.min(maxAllowed, prev + 1);
            widths[floor] = lo + random.nextInt(hi - lo + 1);
        }

        int prev = widths[restFloor - 1];
        int restLo = Math.max(MIN_FLOOR_WIDTH, prev - 1);
        int restHi = Math.min(3, prev + 1);
        widths[restFloor] = restLo + random.nextInt(restHi - restLo + 1);
        widths[FLOOR_COUNT - 1] = 1;
        return widths;
    }

    static List<List<Integer>> assignColumns(int[] widths) {
        List<List<Integer>> columnsByFloor = new ArrayList<>(widths.length);
        for (int width : widths) {
            int start = (MAX_COLUMNS - width) / 2;
            List<Integer> columns = new ArrayList<>(width);
            for (int offset = 0; offset < width; offset++) {
                columns.add(start + offset);
            }
            columnsByFloor.add(List.copyOf(columns));
        }
        return columnsByFloor;
    }

    /**
     * 在可出现精英的层里选出 {@link #ELITE_COUNT} 层，彼此至少隔开一层，
     * 每层再随机挑一个已有房间放精英，避免路线上连续打两个精英。
     */
    static Map<Integer, Integer> placeElites(
            Random random,
            List<List<Integer>> columnsByFloor) {
        int firstEligible = 1;
        int lastEligible = FLOOR_COUNT - 3;
        int eligibleCount = lastEligible - firstEligible + 1;
        int slotCount = eligibleCount - ELITE_COUNT + 1;
        List<Integer> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
        List<Integer> picked = new ArrayList<>(slots.subList(0, ELITE_COUNT));
        Collections.sort(picked);

        Map<Integer, Integer> eliteColumns = new LinkedHashMap<>();
        for (int i = 0; i < ELITE_COUNT; i++) {
            int floor = firstEligible + picked.get(i) + i;
            List<Integer> columns = columnsByFloor.get(floor);
            eliteColumns.put(floor, columns.get(random.nextInt(columns.size())));
        }
        return eliteColumns;
    }

    private static void assignTypes(
            List<List<PlacedNode>> floors,
            List<Map<Integer, List<Integer>>> outgoingByFloor,
            Map<Integer, Integer> eliteColumns,
            Random random) {
        int restFloor = FLOOR_COUNT - 2;
        for (int floor = 0; floor < FLOOR_COUNT; floor++) {
            List<PlacedNode> typed = new ArrayList<>();
            for (PlacedNode placed : floors.get(floor)) {
                Set<MapNodeType> forbidden = EnumSet.noneOf(MapNodeType.class);
                if (floor > 0) {
                    Map<Integer, List<Integer>> incomingFromPrevious =
                            outgoingByFloor.get(floor - 1);
                    for (PlacedNode predecessor : floors.get(floor - 1)) {
                        if (!incomingFromPrevious
                                .getOrDefault(predecessor.id(), List.of())
                                .contains(placed.id())) {
                            continue;
                        }
                        if (predecessor.type() == MapNodeType.SHOP) {
                            forbidden.add(MapNodeType.SHOP);
                        }
                        if (predecessor.type() == MapNodeType.REST) {
                            forbidden.add(MapNodeType.REST);
                        }
                    }
                }
                if (floor + 1 == restFloor) {
                    forbidden.add(MapNodeType.REST);
                }
                typed.add(placed.withType(chooseType(
                        floor, placed.column(), eliteColumns, forbidden, random)));
            }
            floors.set(floor, typed);
        }
    }

    private static Map<Integer, List<Integer>> connect(
            List<PlacedNode> from,
            List<PlacedNode> to,
            Random random) {
        Map<Integer, List<Integer>> outgoing = new LinkedHashMap<>();
        for (PlacedNode source : from) {
            outgoing.put(source.id(), new ArrayList<>());
        }

        int[] incoming = new int[to.size()];
        for (PlacedNode source : from) {
            int best = nearestIndex(to, source.column());
            addEdge(outgoing, incoming, source.id(), to, best);

            List<Integer> forks = new ArrayList<>();
            if (best > 0 && columnDistance(source, to.get(best - 1)) <= 2) {
                forks.add(best - 1);
            }
            if (best + 1 < to.size() && columnDistance(source, to.get(best + 1)) <= 2) {
                forks.add(best + 1);
            }
            if (!forks.isEmpty() && random.nextBoolean()) {
                int fork = forks.get(random.nextInt(forks.size()));
                addEdge(outgoing, incoming, source.id(), to, fork);
            }
        }

        for (int targetIndex = 0; targetIndex < to.size(); targetIndex++) {
            if (incoming[targetIndex] > 0) {
                continue;
            }
            int sourceIndex = nearestIndex(from, to.get(targetIndex).column());
            addEdge(outgoing, incoming, from.get(sourceIndex).id(), to, targetIndex);
        }
        return outgoing;
    }

    private static void addEdge(
            Map<Integer, List<Integer>> outgoing,
            int[] incoming,
            int sourceId,
            List<PlacedNode> to,
            int targetIndex) {
        List<Integer> targets = outgoing.get(sourceId);
        int targetId = to.get(targetIndex).id();
        if (targets.contains(targetId)) {
            return;
        }
        targets.add(targetId);
        incoming[targetIndex]++;
    }

    private static int nearestIndex(List<PlacedNode> nodes, int column) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            int distance = Math.abs(nodes.get(i).column() - column);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int columnDistance(PlacedNode left, PlacedNode right) {
        return Math.abs(left.column() - right.column());
    }

    private static MapNodeType chooseType(
            int floor,
            int column,
            Map<Integer, Integer> eliteColumns,
            Set<MapNodeType> forbidden,
            Random random) {
        if (floor == 0) {
            return MapNodeType.BATTLE;
        }
        if (floor == FLOOR_COUNT - 2) {
            return MapNodeType.REST;
        }
        if (floor == FLOOR_COUNT - 1) {
            return MapNodeType.BOSS;
        }
        if (eliteColumns.getOrDefault(floor, -1) == column) {
            return MapNodeType.ELITE;
        }

        List<MapNodeType> choices = new ArrayList<>();
        for (MapNodeType candidate : List.of(
                MapNodeType.BATTLE,
                MapNodeType.BATTLE,
                MapNodeType.EVENT,
                MapNodeType.REST,
                MapNodeType.SHOP)) {
            if (!forbidden.contains(candidate)) {
                choices.add(candidate);
            }
        }
        if (choices.isEmpty()) {
            return MapNodeType.BATTLE;
        }
        return choices.get(random.nextInt(choices.size()));
    }
}
