package com.roguelike.dungeon.game.enemy.status;

/**
 * 通用计数器状态：只维护层数，不做任何规则结算。
 *
 * <p>用于「施加给玩家」的易伤 / 虚弱 / 中毒，以及需要挂在自己身上的纯计数状态。
 * 真正的数值结算放在玩家状态模块里，AI 这边只负责把层数报上去。</p>
 */
public class StackStatus extends StatusEffect {

    private final boolean decay;

    /**
     * @param id          状态 ID
     * @param displayName 中文展示名
     * @param stacks      初始层数
     * @param decay       是否每回合结束减少 1 层
     */
    public StackStatus(String id, String displayName, int stacks, boolean decay) {
        super(id, displayName, stacks);
        this.decay = decay;
    }

    /** 会逐回合衰减的计数状态。 */
    public static StackStatus decaying(String id, String displayName, int stacks) {
        return new StackStatus(id, displayName, stacks, true);
    }

    /** 永久的计数状态。 */
    public static StackStatus permanent(String id, String displayName, int stacks) {
        return new StackStatus(id, displayName, stacks, false);
    }

    @Override
    public boolean decayEachTurn() {
        return decay;
    }
}
