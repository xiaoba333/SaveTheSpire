package com.roguelike.dungeon.game.enemy.status;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.DamageContext;
import com.roguelike.dungeon.game.enemy.Monster;

/**
 * 骨质疏松（骷髅的初始状态）。
 *
 * <p>设计案原文：<i>受到伤害时额外受到两点伤害，且每次行动都会受到 2 点伤害。</i></p>
 *
 * <p>两条效果分别落在两个钩子上：</p>
 * <ul>
 *   <li>受击加成 → {@link #modifyIncomingDamage}（每层 +2，对所有来源的伤害生效）</li>
 *   <li>行动惩罚 → {@link #onActed}，走 {@link Monster#takeTrueDamage(int)}，
 *       <b>绕过护甲与伤害修正</b>，避免与上面那条互相叠加导致翻倍。</li>
 * </ul>
 */
public final class OsteoporosisStatus extends StatusEffect {

    /** 每层带来的额外受伤与行动自伤。 */
    private static final int PER_STACK = 2;

    public OsteoporosisStatus(int stacks) {
        super(StatusIds.OSTEOPOROSIS, "骨质疏松", stacks);
    }

    @Override
    public void modifyIncomingDamage(DamageContext ctx) {
        ctx.addAmount(PER_STACK * stacks());
    }

    @Override
    public void onActed(Monster owner, BattleContext ctx) {
        int damage = PER_STACK * stacks();
        if (damage <= 0) {
            return;
        }
        owner.takeTrueDamage(damage);
        ctx.log("【骨质疏松】" + owner.displayName() + " 骨骼脆裂，行动时受到 " + damage + " 点伤害");
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
