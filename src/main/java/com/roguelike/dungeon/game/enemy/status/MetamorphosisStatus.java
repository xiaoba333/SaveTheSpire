package com.roguelike.dungeon.game.enemy.status;

/**
 * 蜕变（Boss 凯洛斯蛋链的成长计数）。
 *
 * <p>设计案原文：<i>当蛋孵化时，每有一层蜕变，凯洛斯增加一点力量。</i></p>
 *
 * <p>蛋形态只保留层数，不提供力量。本体破壳时由战斗 AI 把全部层数
 * {@link com.roguelike.dungeon.game.enemy.Monster#addStrength(int) 转化为力量}，
 * 于是凯洛斯的「打0*9」在 4 层蜕变下变成 9 段 4 点伤害。</p>
 */
public final class MetamorphosisStatus extends StatusEffect {

    public MetamorphosisStatus(int stacks) {
        super(StatusIds.METAMORPHOSIS, "蜕变", stacks);
    }

    @Override
    public int strengthBonus() {
        return 0;
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
