package com.roguelike.dungeon.game.enemy.status;

import com.roguelike.dungeon.game.enemy.DamageContext;
import com.roguelike.dungeon.game.enemy.Monster;

/**
 * 蠕动（两条蛆的初始状态）。
 *
 * <p>设计案原文：<i>不会随回合数减少，首次受到攻击时伤害减半，受击后消失。</i></p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>只有「攻击」类伤害才触发减半，状态伤害（如骨质疏松的每回合 2 点）不触发。</li>
 *   <li>一次攻击结算完毕后立即移除，之后不再享受减伤。</li>
 *   <li>{@link #decayEachTurn()} 为 false，因此不会随回合自然消失。</li>
 * </ul>
 */
public final class CreepingStatus extends StatusEffect {

    private boolean triggered;

    public CreepingStatus(int stacks) {
        super(StatusIds.CREEPING, "蠕动", stacks);
    }

    @Override
    public void modifyIncomingDamage(DamageContext ctx) {
        if (!ctx.isAttack() || triggered) {
            return;
        }
        ctx.halve();
        triggered = true;
    }

    @Override
    public void onDamaged(DamageContext ctx, Monster owner) {
        if (triggered) {
            markRemoved();
        }
    }

    @Override
    public boolean decayEachTurn() {
        return false;
    }
}
