package com.roguelike.dungeon.game.enemy.script;

import java.util.Arrays;
import java.util.List;

import com.roguelike.dungeon.game.enemy.intent.Intent;

/**
 * 脚本工厂：把常用写法收敛成一行，写怪物数据时直接用这里的方法。
 *
 * <p>配套的领域语言是 {@code Intents}：{@code Intents} 造「一回合做什么」，
 * {@code Scripts} 造「多个回合怎么排」。</p>
 */
public final class Scripts {

    private Scripts() {
    }

    /** 固定循环：{@code loop(打12, 回6)} = 1-2循环。 */
    public static EnemyScript loop(Intent... intents) {
        return loopFrom(0, intents);
    }

    /**
     * 前 {@code leadingOnce} 个意图只走一次，之后在剩余意图之间循环。
     *
     * <p>{@code loopFrom(1, 给易伤, 打6, 防6)} 表示：第 1 回合给易伤，
     * 之后在「打6 / 防6」之间来回（也就是设计案里的「2-3循环」）。</p>
     */
    public static EnemyScript loopFrom(int leadingOnce, Intent... intents) {
        return new LoopScript(Arrays.asList(intents), leadingOnce, 0);
    }

    /**
     * 两段交错：{@code alternate(打6, 上虚弱, 1)} 让第二只怪物从「上虚弱」起手。
     * 两条蛆的「循环打6，给玩家一层虚弱（交错进行）」靠它实现。
     *
     * @param startOffset 起始偏移，取 0 或 1（更大的值会自动取模）
     */
    public static EnemyScript alternate(Intent first, Intent second, int startOffset) {
        return new LoopScript(List.of(first, second), 0, startOffset);
    }

    /** 一次性脚本：只有一段，之后一直重复它。 */
    public static EnemyScript single(Intent intent) {
        return new LoopScript(List.of(intent), 0, 0);
    }

    /** 从第一阶段开始，构造一个可继续 {@code addPhase} 的分阶段脚本。 */
    public static PhaseScript phase(EnemyScript initialScript) {
        return new PhaseScript(initialScript);
    }

    /** 直接用一堆意图构造分阶段脚本的第一阶段。 */
    public static PhaseScript phase(Intent... initialIntents) {
        return new PhaseScript(loop(initialIntents));
    }
}
