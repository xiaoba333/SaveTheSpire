package com.roguelike.dungeon.game.enemy.script;

import java.util.List;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.intent.Intent;

/**
 * 循环脚本：设计案里 90% 的怪都是这个形状。
 *
 * <p>「1-2循环」「2-3循环」「交错进行」都能用它表达：</p>
 * <pre>{@code
 * Scripts.loop(打12, 回6)                  // 1-2循环：两段来回
 * Scripts.loopFrom(1, 给易伤, 打6, 防6)     // 第1回合单独一次，之后 2-3 循环
 * Scripts.alternate(打6, 上虚弱, 1)          // 交错：从第 2 个意图开始轮
 * }</pre>
 *
 * <p>{@code leadingOnce} 是「开头只走一次、不参与循环」的意图个数。
 * 例如亡灵写的是「意图：（1）给予玩家99层易伤 / 打6 / 防6并获得2力量 / 2-3循环」，
 * 就写成 {@code loopFrom(1, 给易伤, 打6, 防6并获得2力量)}。</p>
 */
public final class LoopScript implements EnemyScript {

    private final List<Intent> intents;
    private final int leadingOnce;
    private final int loopLength;
    private final int startOffset;
    private int played;

    /**
     * @param intents     按顺序排列的意图
     * @param leadingOnce 开头只执行一次的意图个数（0 表示全部参与循环）
     * @param startOffset 循环段内的起始偏移，用于让同类怪物「交错」出手
     */
    public LoopScript(List<Intent> intents, int leadingOnce, int startOffset) {
        if (intents == null || intents.isEmpty()) {
            throw new IllegalArgumentException("决策脚本至少要有一个意图");
        }
        this.intents = List.copyOf(intents);
        // 至少保留最后一个意图参与循环，否则会把索引算到界外
        this.leadingOnce = Math.max(0, Math.min(leadingOnce, this.intents.size() - 1));
        this.loopLength = this.intents.size() - this.leadingOnce;
        this.startOffset = startOffset;
    }

    @Override
    public Intent nextIntent(Monster self, BattleContext ctx) {
        int index;
        if (played < leadingOnce) {
            index = played;
        } else {
            index = leadingOnce + Math.floorMod(startOffset + (played - leadingOnce), loopLength);
        }
        played++;
        return intents.get(index);
    }

    /** 已经产出的意图数量（= 已经过去的回合数）。 */
    public int playedTurns() {
        return played;
    }

    /** 该脚本包含的全部意图（只读）。 */
    public List<Intent> intents() {
        return intents;
    }

    /** 回到第一回合，用于同一份脚本被复用或战斗重开。 */
    public void reset() {
        played = 0;
    }

    @Override
    public String toString() {
        return "LoopScript{intents=" + intents.size() + ", leadingOnce=" + leadingOnce
                + ", startOffset=" + startOffset + "}";
    }
}
