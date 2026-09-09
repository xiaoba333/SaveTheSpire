package com.roguelike.dungeon.game.map;

/** 地图节点在当前游戏进度中的状态。 */
public enum MapNodeState {
    LOCKED,
    AVAILABLE,
    CURRENT,
    COMPLETED
}
