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
        for (int floor = 0; floor < MapGenerator.FLOOR_COUNT - 1; floor++) {
            assertEquals(MapGenerator.COLUMN_COUNT, map.getNodesOnFloor(floor).size());
        }
        assertEquals(1, map.getNodesOnFloor(MapGenerator.FLOOR_COUNT - 1).size());
    }

    @Test
    void startingFloorShouldContainOnlyBattleNodes() {
        DungeonMap map = new MapGenerator().generate(12345L);

        assertEquals(MapGenerator.COLUMN_COUNT, map.getStartingNodes().size());
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
        DungeonMap map = new MapGenerator().generate(12345L);

        for (MapNode node : map.getNodes()) {
            if (node.type() != MapNodeType.BOSS) {
                assertFalse(node.nextNodeIds().isEmpty());
            }

            for (int nextNodeId : node.nextNodeIds()) {
                MapNode target = map.getNode(nextNodeId);
                assertEquals(node.floor() + 1, target.floor());
            }
        }
    }

    @Test
    void everyGeneratedNodeShouldBeReachableFromStartingFloor() {
        DungeonMap map = new MapGenerator().generate(12345L);
        Set<Integer> reachableNodeIds = new HashSet<>();
        map.getStartingNodes().forEach(node -> visit(map, node, reachableNodeIds));

        assertEquals(map.getNodes().size(), reachableNodeIds.size());
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
