package com.roguelike.dungeon.game.enemy.status;

/**
 * 复苏（精英怪「巨人遗骸」血量跌到 20 以下后获得）。
 *
 * <p>设计案原文：<i>所有行动判定两次。</i></p>
 *
 * <p>框架的处理方式是放大「行动次数倍率」：{@code Monster.takeTurn} 会按
 * {@link #actionRepeatMultiplier()} 把同一个意图连续执行两次，日志里能清楚看到
 * 「打6，给予一层易伤」整条意图走了两遍。</p>
 *
 * <p class="note">待确认：如果策划的本意是「多段攻击的段数翻倍」而不是「整条意图走两遍」，
 * 只需改这一个类即可，其它代码不用动。</p>
 */
public final class RevivalStatus extends StatusEffect {

    public RevivalStatus(int stacks) {
        super(StatusIds.REVIVAL, "复苏", stacks);
    }

    @Override
    public int actionRepeatMultiplier() {
        return 1 + stacks();
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
