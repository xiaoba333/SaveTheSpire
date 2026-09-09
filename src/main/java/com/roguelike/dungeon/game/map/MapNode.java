package com.roguelike.dungeon.game.map;

import java.util.List;
import java.util.Objects;

/**
 * 地图中的一个关卡节点。
 *
 * @param id 唯一编号
 * @param floor 所在层，从 0 开始
 * @param column 所在列，从 0 开始
 * @param type 关卡类型
 * @param nextNodeIds 可以前往的下一批节点编号
 */
public record MapNode(
        int id,
        int floor,
        int column,
        MapNodeType type,
        List<Integer> nextNodeIds) {

    public MapNode {
        if (id < 0) {
            throw new IllegalArgumentException("节点编号不能为负数");
        }
        if (floor < 0 || column < 0) {
            throw new IllegalArgumentException("节点层数和列数不能为负数");
        }
        type = Objects.requireNonNull(type, "节点类型不能为 null");
        nextNodeIds = List.copyOf(Objects.requireNonNull(nextNodeIds, "后继节点不能为 null"));
    }
}
