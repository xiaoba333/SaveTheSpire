package com.roguelike.dungeon.flow;

/** 关卡模块完成处理后，用于通知游戏流程继续推进。 */
@FunctionalInterface
public interface LevelFinishHandler {

    /**
     * 上报关卡处理结果。每个关卡控制器应只调用一次。
     *
     * @param result 关卡结果
     */
    void onLevelFinished(LevelResult result);
}
