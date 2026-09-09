package com.roguelike.dungeon.game.map;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapServiceTest {

    @Test
    void newServiceShouldExposeStartingNodesAsAvailable() {
        MapService service = new MapService(12345L);

        assertEquals(MapGenerator.COLUMN_COUNT, service.getAvailableNodes().size());
        assertTrue(service.getAvailableNodes().stream()
                .allMatch(node -> service.getNodeState(node.id()) == MapNodeState.AVAILABLE));
        assertTrue(service.getCurrentNode().isEmpty());
    }

    @Test
    void selectingNodeShouldMakeItCurrentAndBlockOtherSelections() {
        MapService service = new MapService(12345L);
        MapNode selected = service.getAvailableNodes().getFirst();

        MapNode result = service.selectNode(selected.id());

        assertEquals(selected, result);
        assertEquals(selected, service.getCurrentNode().orElseThrow());
        assertEquals(MapNodeState.CURRENT, service.getNodeState(selected.id()));
        assertTrue(service.getAvailableNodes().isEmpty());
    }

    @Test
    void selectingLockedNodeShouldFail() {
        MapService service = new MapService(12345L);
        MapNode lockedNode = service.getDungeonMap().getNodesOnFloor(1).getFirst();

        assertFalse(service.canSelectNode(lockedNode.id()));
        assertThrows(IllegalStateException.class,
                () -> service.selectNode(lockedNode.id()));
    }

    @Test
    void completingCurrentNodeShouldUnlockOnlyItsNextNodes() {
        MapService service = new MapService(12345L);
        MapNode selected = service.getAvailableNodes().getFirst();
        service.selectNode(selected.id());

        MapNode completed = service.completeCurrentNode();

        assertEquals(selected, completed);
        assertEquals(MapNodeState.COMPLETED, service.getNodeState(selected.id()));
        assertEquals(Set.copyOf(selected.nextNodeIds()),
                service.getAvailableNodes().stream()
                        .map(MapNode::id)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void completingWithoutCurrentNodeShouldFail() {
        MapService service = new MapService(12345L);

        assertThrows(IllegalStateException.class, service::completeCurrentNode);
    }
}
