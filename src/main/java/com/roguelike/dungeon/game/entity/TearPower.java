package com.roguelike.dungeon.game.entity;

/**
 * 能力「撕裂」：玩家对自己造成伤害时获得力量。
 */
public final class TearPower implements Power {

    private final int strengthAmount;

    public TearPower(int strengthAmount) {
        this.strengthAmount = strengthAmount;
    }

    @Override
    public String name() {
        return "撕裂";
    }

    @Override
    public String description() {
        return "对自己造成伤害时，力量 +" + strengthAmount + "。";
    }

    @Override
    public void onSelfDamage(Player player, int damage) {
        player.addStacks(StatusEffect.STRENGTH, strengthAmount);
    }
}
