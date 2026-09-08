package com.roguelike.dungeon.game.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 创建可复现的基础关卡地图。 */
public final class MapGenerator {
    public static final int FLOOR_COUNT = 7;
    public static final int COLUMN_COUNT = 3;

    /**
     * 根据随机种子生成地图。同一个种子始终生成相同的地图，方便联调和测试。
     */
    public DungeonMap generate(long seed) {
        Random random = new Random(seed);
        List<MapNode> nodes = new ArrayList<>();

        int bossId = (FLOOR_COUNT - 1) * COLUMN_COUNT;
        for (int floor = 0; floor < FLOOR_COUNT - 1; floor++) {
            for (int column = 0; column < COLUMN_COUNT; column++) {
                int id = nodeId(floor, column);
                List<Integer> nextNodeIds = nextNodeIds(floor, column, bossId, random);
                nodes.add(new MapNode(id, floor, column,
                        chooseType(floor, random), nextNodeIds));
            }
        }
        nodes.add(new MapNode(bossId, FLOOR_COUNT - 1, 1,
                MapNodeType.BOSS, List.of()));
        return new DungeonMap(nodes);
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

    private static MapNodeType chooseType(int floor, Random random) {
        if (floor == 0) {
            return MapNodeType.BATTLE;
        }
        if (floor == FLOOR_COUNT - 2) {
            return MapNodeType.REST;
        }

        MapNodeType[] choices = {
                MapNodeType.BATTLE,
                MapNodeType.BATTLE,
                MapNodeType.EVENT,
                MapNodeType.REST,
                MapNodeType.SHOP,
                MapNodeType.ELITE
        };
        return choices[random.nextInt(choices.length)];
    }

    private static int nodeId(int floor, int column) {
        return floor * COLUMN_COUNT + column;
    }
}
