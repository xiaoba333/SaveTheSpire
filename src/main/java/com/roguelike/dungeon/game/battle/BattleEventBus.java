package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.flow.LevelResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 战斗结束事件总线。
 *
 * <p>替换 {@code Combat.notifyFinished} 的硬回调：订阅方自行注册，
 * 发布方只负责把 {@link LevelResult} 广播出去。同一场战斗只发布一次。</p>
 */
public final class BattleEventBus {

    private final List<LevelFinishHandler> finishListeners = new ArrayList<>();
    private boolean finishPublished;

    /** 注册战斗结束监听。同一监听重复注册会被调用多次。 */
    public void subscribeFinished(LevelFinishHandler handler) {
        finishListeners.add(Objects.requireNonNull(handler, "结束监听不能为 null"));
    }

    /**
     * 发布战斗结束。首次之后的调用会被忽略，保证只通知一次。
     */
    public void publishFinished(LevelResult result) {
        Objects.requireNonNull(result, "关卡结果不能为 null");
        if (finishPublished) {
            return;
        }
        finishPublished = true;
        for (LevelFinishHandler handler : List.copyOf(finishListeners)) {
            handler.onLevelFinished(result);
        }
    }

    /** 是否已经发布过结束事件。 */
    public boolean isFinishPublished() {
        return finishPublished;
    }
}
