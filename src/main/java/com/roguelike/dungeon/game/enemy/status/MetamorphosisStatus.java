package com.roguelike.dungeon.game.enemy.status;

/**
 * 蜕变（Boss 凯洛斯的成长状态）。
 *
 * <p>设计案原文：<i>当蛋孵化时，每有一层蜕变，凯洛斯增加一点力量。</i></p>
 *
 * <p>因此它不改伤害、不改行动，只做一件事：把层数换算成
 * {@link #strengthBonus()}。{@link com.roguelike.dungeon.game.enemy.Monster#getStrength()}
 * 会把所有状态的加成求和，攻击意图里用 {@code 基础伤害 + 力量} 计算最终伤害——
 * 于是凯洛斯的「打0*9」在 4 层蜕变的加持下变成 9 段 4 点伤害。</p>
 */
public final class MetamorphosisStatus extends StatusEffect {

    public MetamorphosisStatus(int stacks) {
        super(StatusIds.METAMORPHOSIS, "蜕变", stacks);
    }

    @Override
    public int strengthBonus() {
        return stacks();
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
