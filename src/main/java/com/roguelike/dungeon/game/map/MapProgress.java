package com.roguelike.dungeon.game.map;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 管理玩家在一张地图上的当前位置、已完成节点和可选节点。 */
public final class MapProgress {
    private final DungeonMap dungeonMap;
    private final Set<Integer> completedNodeIds = new LinkedHashSet<>();
    private final Set<Integer> availableNodeIds = new LinkedHashSet<>();
    private Integer currentNodeId;

    public MapProgress(DungeonMap dungeonMap) {
        this.dungeonMap = Objects.requireNonNull(dungeonMap, "地图不能为 null");
        dungeonMap.getStartingNodes().forEach(node -> availableNodeIds.add(node.id()));
    }

    /** 判断节点当前是否可以进入。 */
    public boolean canEnter(int nodeId) {
        return currentNodeId == null && availableNodeIds.contains(nodeId);
    }

    /** 进入一个可选节点。 */
    public MapNode enter(int nodeId) {
        if (!canEnter(nodeId)) {
            throw new IllegalStateException("节点当前不可进入: " + nodeId);
        }
        availableNodeIds.clear();
        currentNodeId = nodeId;
        return dungeonMap.getNode(nodeId);
    }

    /**
     * 完成当前节点，并解锁该节点连接的后继节点。
     *
     * @return 刚刚完成的节点
     */
    public MapNode completeCurrentNode() {
        if (currentNodeId == null) {
            throw new IllegalStateException("当前没有正在进行的地图节点");
        }

        MapNode completedNode = dungeonMap.getNode(currentNodeId);
        completedNodeIds.add(currentNodeId);
        availableNodeIds.addAll(completedNode.nextNodeIds());
        currentNodeId = null;
        return completedNode;
    }

    /** 查询节点当前的显示状态。 */
    public MapNodeState getState(int nodeId) {
        dungeonMap.getNode(nodeId);
        if (Objects.equals(currentNodeId, nodeId)) {
            return MapNodeState.CURRENT;
        }
        if (completedNodeIds.contains(nodeId)) {
            return MapNodeState.COMPLETED;
        }
        if (availableNodeIds.contains(nodeId)) {
            return MapNodeState.AVAILABLE;
        }
        return MapNodeState.LOCKED;
    }

    public Optional<MapNode> getCurrentNode() {
        return currentNodeId == null
                ? Optional.empty()
                : Optional.of(dungeonMap.getNode(currentNodeId));
    }

    public Set<Integer> getCompletedNodeIds() {
        return Collections.unmodifiableSet(completedNodeIds);
    }

    public Set<Integer> getAvailableNodeIds() {
        return Collections.unmodifiableSet(availableNodeIds);
    }
}
