package com.roguelike.dungeon.game.map;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** 将地图结构和当前进度渲染为便于调试的纯文字。 */
public final class MapTextRenderer {

    /**
     * 从最高层向入口层输出节点，并在下方列出所有路线。
     *
     * @param mapService 地图逻辑服务
     * @return 地图文字快照
     */
    public String render(MapService mapService) {
        Objects.requireNonNull(mapService, "地图服务不能为 null");

        StringBuilder output = new StringBuilder("=== 地图 ===\n");
        appendFloors(output, mapService);
        output.append("\n路线：\n");
        appendConnections(output, mapService);
        return output.toString();
    }

    private static void appendFloors(StringBuilder output, MapService mapService) {
        DungeonMap dungeonMap = mapService.getDungeonMap();
        int maxColumn = dungeonMap.getNodes().stream()
                .mapToInt(MapNode::column)
                .max()
                .orElse(0);

        for (int floor = dungeonMap.getMaxFloor(); floor >= 0; floor--) {
            Map<Integer, MapNode> nodesByColumn = new HashMap<>();
            for (MapNode node : dungeonMap.getNodesOnFloor(floor)) {
                nodesByColumn.put(node.column(), node);
            }

            StringJoiner cells = new StringJoiner(" | ");
            for (int column = 0; column <= maxColumn; column++) {
                MapNode node = nodesByColumn.get(column);
                cells.add(node == null ? "·" : formatNode(node, mapService.getNodeState(node.id())));
            }
            output.append("第 ")
                    .append(floor + 1)
                    .append(" 层 | ")
                    .append(cells)
                    .append('\n');
        }
    }

    private static void appendConnections(StringBuilder output, MapService mapService) {
        for (MapNode node : mapService.getNodes()) {
            output.append(formatId(node.id())).append(" -> ");
            if (node.nextNodeIds().isEmpty()) {
                output.append("终点");
            } else {
                StringJoiner targets = new StringJoiner(", ");
                node.nextNodeIds().stream()
                        .map(MapTextRenderer::formatId)
                        .forEach(targets::add);
                output.append(targets);
            }
            output.append('\n');
        }
    }

    private static String formatNode(MapNode node, MapNodeState state) {
        return "[" + formatId(node.id())
                + " " + typeLabel(node.type())
                + " " + stateLabel(state) + "]";
    }

    private static String formatId(int id) {
        return String.format("%02d", id);
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

    private static String stateLabel(MapNodeState state) {
        return switch (state) {
            case LOCKED -> "锁定";
            case AVAILABLE -> "可选";
            case CURRENT -> "进行中";
            case COMPLETED -> "已完成";
        };
    }
}
