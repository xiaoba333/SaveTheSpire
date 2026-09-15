package com.roguelike.dungeon.game.map;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapGeneratorTest {

    @Test
    void sameSeedShouldGenerateSameMap() {
        MapGenerator generator = new MapGenerator();

        DungeonMap firstMap = generator.generate(12345L);
        DungeonMap secondMap = generator.generate(12345L);

        assertEquals(firstMap.getNodes(), secondMap.getNodes());
    }

    @Test
    void generatedMapShouldHaveExpectedFloorStructure() {
        DungeonMap map = new MapGenerator().generate(12345L);

        assertEquals(MapGenerator.FLOOR_COUNT - 1, map.getMaxFloor());
        int startWidth = map.getStartingNodes().size();
        assertTrue(startWidth >= MapGenerator.MIN_FLOOR_WIDTH && startWidth <= 3);
        for (int floor = 0; floor < MapGenerator.FLOOR_COUNT - 1; floor++) {
            int width = map.getNodesOnFloor(floor).size();
            assertTrue(width >= MapGenerator.MIN_FLOOR_WIDTH
                            && width <= MapGenerator.MAX_FLOOR_WIDTH,
                    "第 " + floor + " 层房间数 " + width);
        }
        assertEquals(1, map.getNodesOnFloor(MapGenerator.FLOOR_COUNT - 1).size());
        int restWidth = map.getNodesOnFloor(MapGenerator.FLOOR_COUNT - 2).size();
        assertTrue(restWidth >= MapGenerator.MIN_FLOOR_WIDTH && restWidth <= 3);
    }

    @Test
    void adjacentFloorsShouldChangeWidthByAtMostOne() {
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);
            int previousWidth = map.getNodesOnFloor(0).size();
            for (int floor = 1; floor < MapGenerator.FLOOR_COUNT - 1; floor++) {
                int width = map.getNodesOnFloor(floor).size();
                assertTrue(Math.abs(width - previousWidth) <= 1,
                        "种子 " + seed + " 第 " + floor + " 层从 "
                                + previousWidth + " 间变成 " + width + " 间");
                previousWidth = width;
            }
        }
    }

    @Test
    void someSeedsShouldWidenAndNarrow() {
        boolean sawWide = false;
        boolean sawNarrow = false;
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);
            for (int floor = 0; floor < MapGenerator.FLOOR_COUNT - 1; floor++) {
                int width = map.getNodesOnFloor(floor).size();
                if (width >= 4) {
                    sawWide = true;
                }
                if (width == MapGenerator.MIN_FLOOR_WIDTH) {
                    sawNarrow = true;
                }
            }
        }
        assertTrue(sawWide, "50 个种子里应出现至少一层 4 间或更宽");
        assertTrue(sawNarrow, "50 个种子里应出现至少一层只有 2 间");
    }

    @Test
    void startingFloorShouldContainOnlyBattleNodes() {
        DungeonMap map = new MapGenerator().generate(12345L);

        int startWidth = map.getStartingNodes().size();
        assertTrue(startWidth >= MapGenerator.MIN_FLOOR_WIDTH && startWidth <= 3);
        assertTrue(map.getStartingNodes().stream()
                .allMatch(node -> node.type() == MapNodeType.BATTLE));
    }

    @Test
    void topFloorShouldContainOneBossWithoutNextNode() {
        DungeonMap map = new MapGenerator().generate(12345L);

        var topFloorNodes = map.getNodesOnFloor(map.getMaxFloor());

        assertEquals(1, topFloorNodes.size());
        MapNode boss = topFloorNodes.getFirst();
        assertEquals(MapNodeType.BOSS, boss.type());
        assertTrue(boss.nextNodeIds().isEmpty());
    }

    @Test
    void everyConnectionShouldLeadToExistingNodeOnNextFloor() {
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);

            for (MapNode node : map.getNodes()) {
                if (node.type() != MapNodeType.BOSS) {
                    assertFalse(node.nextNodeIds().isEmpty(), "种子 " + seed);
                }

                for (int nextNodeId : node.nextNodeIds()) {
                    MapNode target = map.getNode(nextNodeId);
                    assertEquals(node.floor() + 1, target.floor(), "种子 " + seed);
                }
            }
        }
    }

    @Test
    void everyGeneratedNodeShouldBeReachableFromStartingFloor() {
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);
            Set<Integer> reachableNodeIds = new HashSet<>();
            map.getStartingNodes().forEach(node -> visit(map, node, reachableNodeIds));

            assertEquals(map.getNodes().size(), reachableNodeIds.size(), "种子 " + seed);
        }
    }

    @Test
    void eachActShouldHaveExactlyThreeNonConsecutiveElites() {
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);
            List<MapNode> elites = map.getNodes().stream()
                    .filter(node -> node.type() == MapNodeType.ELITE)
                    .toList();

            assertEquals(MapGenerator.ELITE_COUNT, elites.size(), "种子 " + seed);
            assertTrue(elites.stream().noneMatch(node -> node.floor() == 0),
                    "第一层不能出现精英");
            assertTrue(elites.stream().noneMatch(node ->
                            node.floor() == MapGenerator.FLOOR_COUNT - 2
                                    || node.floor() == MapGenerator.FLOOR_COUNT - 1),
                    "休息层和 Boss 层不能出现精英");

            Set<Integer> eliteFloors = new HashSet<>();
            for (MapNode elite : elites) {
                assertTrue(eliteFloors.add(elite.floor()),
                        "同一层不能放两个精英：" + elite.floor());
            }
            List<Integer> floors = eliteFloors.stream().sorted().toList();
            for (int i = 1; i < floors.size(); i++) {
                assertTrue(floors.get(i) - floors.get(i - 1) >= 2,
                        "精英不能连续：种子 " + seed + " 层 " + floors);
            }
        }
    }

    @Test
    void pathsShouldNotChainShopOrRestRooms() {
        for (long seed = 0; seed < 50; seed++) {
            DungeonMap map = new MapGenerator().generate(seed);
            for (MapNode node : map.getNodes()) {
                for (int nextNodeId : node.nextNodeIds()) {
                    MapNode target = map.getNode(nextNodeId);
                    assertFalse(node.type() == MapNodeType.SHOP
                                    && target.type() == MapNodeType.SHOP,
                            "种子 " + seed + " 商店相连："
                                    + node.id() + " -> " + target.id());
                    assertFalse(node.type() == MapNodeType.REST
                                    && target.type() == MapNodeType.REST,
                            "种子 " + seed + " 火堆相连："
                                    + node.id() + " -> " + target.id());
                }
            }
        }
    }

    private static void visit(
            DungeonMap map,
            MapNode node,
            Set<Integer> visitedNodeIds) {
        if (!visitedNodeIds.add(node.id())) {
            return;
        }
        node.nextNodeIds().stream()
                .map(map::getNode)
                .forEach(nextNode -> visit(map, nextNode, visitedNodeIds));
    }
}
