package com.roguelike.dungeon.game.entity;

/**
 * 能力「血之高兴」：最大生命值下降时，改为提高最大生命值。
 */
public final class BloodHappinessPower implements Power {

    @Override
    public String name() {
        return "血之高兴";
    }

    @Override
    public String description() {
        return "当你减少自己血量上限时，改为血量上限 +1。";
    }

    @Override
    public boolean onMaxHealthReduced(Player player, int amount) {
        player.increaseMaxHealth(amount);
        return true;
    }
}
