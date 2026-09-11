package com.roguelike.dungeon.game.enemy.status;

/**
 * 无灵（精英怪「巨人遗骸」的初始状态）。
 *
 * <p>设计案原文：<i>无法被易伤、虚弱、中毒。</i></p>
 *
 * <p>实现方式是「拦截施加」而不是「施加后清除」：{@link Monster#applyStatus}
 * 在真正挂状态之前会先问一遍所有状态的 {@link #blocksStatus(String)}，
 * 只要有一个返回 true 就整体拒绝，因此巨人遗骸身上永远不会出现这三种状态。</p>
 *
 * <p>当血量跌到 20 以下时，剧情需要「无灵状态消失」，直接由
 * {@code PhaseScript} 的进入动作 {@code removeOnEnter} 移除即可。</p>
 */
public final class SoullessStatus extends StatusEffect {

    public SoullessStatus(int stacks) {
        super(StatusIds.SOULLESS, "无灵", stacks);
    }

    @Override
    public boolean blocksStatus(String statusId) {
        return StatusIds.VULNERABLE.equals(statusId)
                || StatusIds.WEAK.equals(statusId)
                || StatusIds.POISON.equals(statusId);
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
