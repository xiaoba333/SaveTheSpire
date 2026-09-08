package com.roguelike.dungeon.game.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存一局游戏中的全部地图节点及其路线。 */
public final class DungeonMap {
    private final Map<Integer, MapNode> nodesById;

    public DungeonMap(Collection<MapNode> nodes) {
        Objects.requireNonNull(nodes, "地图节点不能为 null");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("地图至少需要一个节点");
        }

        Map<Integer, MapNode> indexedNodes = new LinkedHashMap<>();
        for (MapNode node : nodes) {
            MapNode previous = indexedNodes.put(node.id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("存在重复节点编号: " + node.id());
            }
        }
        validateConnections(indexedNodes);
        nodesById = Collections.unmodifiableMap(indexedNodes);
    }

    /** 返回全部节点，顺序与创建地图时一致。 */
    public List<MapNode> getNodes() {
        return List.copyOf(nodesById.values());
    }

    /** 根据编号取得节点，编号不存在时抛出异常。 */
    public MapNode getNode(int nodeId) {
        MapNode node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("未知地图节点: " + nodeId);
        }
        return node;
    }

    /** 返回指定层的全部节点，并按列排序。 */
    public List<MapNode> getNodesOnFloor(int floor) {
        return nodesById.values().stream()
                .filter(node -> node.floor() == floor)
                .sorted(Comparator.comparingInt(MapNode::column))
                .toList();
    }

    /** 返回地图最底层的入口节点。 */
    public List<MapNode> getStartingNodes() {
        int firstFloor = nodesById.values().stream()
                .mapToInt(MapNode::floor)
                .min()
                .orElseThrow();
        return getNodesOnFloor(firstFloor);
    }

    /** 地图的最高层编号。 */
    public int getMaxFloor() {
        return nodesById.values().stream()
                .mapToInt(MapNode::floor)
                .max()
                .orElseThrow();
    }

    private static void validateConnections(Map<Integer, MapNode> nodes) {
        List<Integer> invalidTargets = new ArrayList<>();
        for (MapNode node : nodes.values()) {
            for (int nextNodeId : node.nextNodeIds()) {
                if (!nodes.containsKey(nextNodeId)) {
                    invalidTargets.add(nextNodeId);
                }
            }
        }
        if (!invalidTargets.isEmpty()) {
            throw new IllegalArgumentException("路线指向不存在的节点: " + invalidTargets);
        }
    }
}
