package com.roguelike.dungeon.game.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTextRendererTest {
    private final MapTextRenderer renderer = new MapTextRenderer();

    @Test
    void initialMapShouldShowFloorsBossAvailableStartsAndConnections() {
        MapService service = new MapService(12345L);
        MapNode boss = service.getNodes().stream()
                .filter(node -> node.type() == MapNodeType.BOSS)
                .findFirst()
                .orElseThrow();

        String text = renderer.render(service);

        assertTrue(text.contains("第 " + MapGenerator.FLOOR_COUNT + " 层"));
        assertTrue(text.contains(nodeText(boss, "Boss", "锁定")));
        for (MapNode start : service.getDungeonMap().getStartingNodes()) {
            assertTrue(text.contains(nodeText(start, "战斗", "可选")));
        }
        assertTrue(text.contains("路线："));
        assertTrue(text.contains(routeText(service.getNodes().getFirst())));
    }

    @Test
    void selectedNodeShouldBeRenderedAsCurrent() {
        MapService service = new MapService(12345L);
        MapNode start = service.getAvailableNodes().getFirst();

        service.selectNode(start.id());

        assertTrue(renderer.render(service)
                .contains(nodeText(start, "战斗", "进行中")));
    }

    @Test
    void completedNodeAndUnlockedTargetsShouldUseLatestStates() {
        MapService service = new MapService(12345L);
        MapNode start = service.getAvailableNodes().getFirst();
        service.selectNode(start.id());
        service.completeCurrentNode();

        String text = renderer.render(service);

        assertTrue(text.contains(nodeText(start, "战斗", "已完成")));
        for (int nextNodeId : start.nextNodeIds()) {
            MapNode next = service.getNode(nextNodeId);
            assertTrue(text.contains(nodeText(next, typeLabel(next.type()), "可选")));
        }
    }

    private static String nodeText(MapNode node, String type, String state) {
        return "[" + String.format("%02d", node.id())
                + " " + type + " " + state + "]";
    }

    private static String routeText(MapNode node) {
        String targets = node.nextNodeIds().stream()
                .map(id -> String.format("%02d", id))
                .reduce((left, right) -> left + ", " + right)
                .orElse("终点");
        return String.format("%02d", node.id()) + " -> " + targets;
    }

    private static String typeLabel(MapNodeType type) {
        return switch (type) {
            case BATTLE -> "战斗";
            case ELITE -> "精英";
            case EVENT -> "事件";
            case REST -> "休息";
            case SHOP -> "商店";
            case BOSS -> "Boss";
        };
    }
}
