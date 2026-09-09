package com.roguelike.dungeon.flow;

/** 关卡结束后提交给游戏流程模块的统一结果。 */
public enum LevelResult {
    /** 关卡成功完成，可以继续奖励、解锁节点或章节结算。 */
    COMPLETED,

    /** 玩家失败，不完成当前地图节点。 */
    DEFEATED
}
