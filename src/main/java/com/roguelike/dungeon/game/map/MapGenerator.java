package com.roguelike.dungeon.game.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 创建可复现的基础关卡地图。 */
public final class MapGenerator {
    public static final int FLOOR_COUNT = 14;
    public static final int COLUMN_COUNT = 3;
    /** 每一章地图固定放置的精英房数量。 */
    public static final int ELITE_COUNT = 3;

    /**
     * 根据随机种子生成地图。同一个种子始终生成相同的地图，方便联调和测试。
     */
    public DungeonMap generate(long seed) {
        Random random = new Random(seed);
        List<MapNode> nodes = new ArrayList<>();
        Map<Integer, Integer> eliteColumns = placeElites(random);

        int bossId = (FLOOR_COUNT - 1) * COLUMN_COUNT;
        for (int floor = 0; floor < FLOOR_COUNT - 1; floor++) {
            for (int column = 0; column < COLUMN_COUNT; column++) {
                int id = nodeId(floor, column);
                List<Integer> nextNodeIds = nextNodeIds(floor, column, bossId, random);
                nodes.add(new MapNode(id, floor, column,
                        chooseType(floor, column, eliteColumns, random), nextNodeIds));
            }
        }
        nodes.add(new MapNode(bossId, FLOOR_COUNT - 1, 1,
                MapNodeType.BOSS, List.of()));
        return new DungeonMap(nodes);
    }

    /**
     * 在可出现精英的层里选出 {@link #ELITE_COUNT} 层，彼此至少隔开一层，
     * 每层再随机挑一列放精英，避免路线上连续打两个精英。
     */
    static Map<Integer, Integer> placeElites(Random random) {
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
            eliteColumns.put(floor, random.nextInt(COLUMN_COUNT));
        }
        return eliteColumns;
    }

    private static List<Integer> nextNodeIds(
            int floor, int column, int bossId, Random random) {
        if (floor == FLOOR_COUNT - 2) {
            return List.of(bossId);
        }

        List<Integer> targets = new ArrayList<>();
        targets.add(nodeId(floor + 1, column));

        int neighbour = random.nextBoolean() ? column - 1 : column + 1;
        if (neighbour >= 0 && neighbour < COLUMN_COUNT) {
            targets.add(nodeId(floor + 1, neighbour));
        }
        return targets;
    }

    private static MapNodeType chooseType(
            int floor,
            int column,
            Map<Integer, Integer> eliteColumns,
            Random random) {
        if (floor == 0) {
            return MapNodeType.BATTLE;
        }
        if (floor == FLOOR_COUNT - 2) {
            return MapNodeType.REST;
        }
        if (eliteColumns.getOrDefault(floor, -1) == column) {
            return MapNodeType.ELITE;
        }

        MapNodeType[] choices = {
                MapNodeType.BATTLE,
                MapNodeType.BATTLE,
                MapNodeType.EVENT,
                MapNodeType.REST,
                MapNodeType.SHOP
        };
        return choices[random.nextInt(choices.length)];
    }

    private static int nodeId(int floor, int column) {
        return floor * COLUMN_COUNT + column;
    }
}
