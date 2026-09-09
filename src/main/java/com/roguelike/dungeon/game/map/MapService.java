package com.roguelike.dungeon.game.map;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 地图模块对外提供的纯逻辑入口。
 *
 * <p>调用方只需要提交节点编号并读取返回的数据，不需要了解
 * {@link MapProgress} 的内部状态，也不依赖任何 UI 框架。</p>
 */
public final class MapService {
    private final DungeonMap dungeonMap;
    private final MapProgress progress;

    /** 根据随机种子创建一张新地图。 */
    public MapService(long seed) {
        this(new MapGenerator().generate(seed));
    }

    /** 使用指定地图创建地图服务，主要用于测试、读档和固定地图。 */
    public MapService(DungeonMap dungeonMap) {
        this.dungeonMap = Objects.requireNonNull(dungeonMap, "地图不能为 null");
        this.progress = new MapProgress(dungeonMap);
    }

    /** 返回整张地图。DungeonMap 及其节点均为只读数据。 */
    public DungeonMap getDungeonMap() {
        return dungeonMap;
    }

    /** 返回全部地图节点。 */
    public List<MapNode> getNodes() {
        return dungeonMap.getNodes();
    }

    /** 根据编号读取节点。 */
    public MapNode getNode(int nodeId) {
        return dungeonMap.getNode(nodeId);
    }

    /** 返回节点当前的进度状态。 */
    public MapNodeState getNodeState(int nodeId) {
        return progress.getState(nodeId);
    }

    /** 返回当前可以选择的节点。 */
    public List<MapNode> getAvailableNodes() {
        return progress.getAvailableNodeIds().stream()
                .map(dungeonMap::getNode)
                .toList();
    }

    /** 返回当前正在进行的节点；尚未选择节点时为空。 */
    public Optional<MapNode> getCurrentNode() {
        return progress.getCurrentNode();
    }

    /** 返回已完成节点编号的不可变快照。 */
    public Set<Integer> getCompletedNodeIds() {
        return Set.copyOf(progress.getCompletedNodeIds());
    }

    /** 判断节点当前是否可以选择。 */
    public boolean canSelectNode(int nodeId) {
        return progress.canEnter(nodeId);
    }

    /**
     * 选择并进入一个可达节点。
     *
     * @throws IllegalStateException 节点锁定或已有正在进行的节点
     */
    public MapNode selectNode(int nodeId) {
        return progress.enter(nodeId);
    }

    /**
     * 完成当前节点并解锁它连接的后继节点。
     *
     * @throws IllegalStateException 当前没有正在进行的节点
     */
    public MapNode completeCurrentNode() {
        return progress.completeCurrentNode();
    }
}
