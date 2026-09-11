package com.roguelike.dungeon.game.entity;

/**
 * 遗物「燃烧之血」：战斗结束时回复生命。
 */
public final class BattleEndHealRelic implements Relic {
    public static final int HEAL_AMOUNT = 6;

    @Override
    public String name() {
        return "燃烧之血";
    }

    @Override
    public String description() {
        return "战斗结束时回复 " + HEAL_AMOUNT + " 点生命";
    }

    @Override
    public void onBattleEnd(Player player) {
        player.heal(HEAL_AMOUNT);
    }
}
