package com.roguelike.dungeon.game.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapProgressTest {

    @Test
    void completingNodeShouldUnlockItsNextNodes() {
        DungeonMap map = new MapGenerator().generate(12345L);
        MapProgress progress = new MapProgress(map);

        MapNode startNode = map.getStartingNodes().getFirst();

        assertEquals(
                MapNodeState.AVAILABLE,
                progress.getState(startNode.id())
        );

        progress.enter(startNode.id());

        assertEquals(
                MapNodeState.CURRENT,
                progress.getState(startNode.id())
        );

        progress.completeCurrentNode();

        assertEquals(
                MapNodeState.COMPLETED,
                progress.getState(startNode.id())
        );

        for (int nextNodeId : startNode.nextNodeIds()) {
            assertEquals(
                    MapNodeState.AVAILABLE,
                    progress.getState(nextNodeId)
            );
        }
    }
}
