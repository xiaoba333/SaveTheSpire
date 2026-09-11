package com.roguelike.dungeon.game.entity;

import java.util.Set;

/**
 * 遗物「燃烧之血」：战斗胜利结束时回复生命。
 */
public final class BattleEndHealRelic implements Relic {
    public static final String ID = "burning_blood";
    public static final int HEAL_AMOUNT = 6;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "燃烧之血";
    }

    @Override
    public String description() {
        return "战斗结束时回复 " + HEAL_AMOUNT + " 点生命";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.COMMON;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.BATTLE_END);
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (trigger != RelicTrigger.BATTLE_END) {
            return;
        }
        ctx.player().heal(HEAL_AMOUNT);
        ctx.log("「燃烧之血」触发：回复 " + HEAL_AMOUNT + " 点生命。");
    }

    @Override
    public void onBattleEnd(Player player) {
        player.heal(HEAL_AMOUNT);
    }
}
