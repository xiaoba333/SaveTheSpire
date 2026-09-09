package com.roguelike.dungeon.game.entity;

/**
 * 遗物「血之代价」：进入关卡时回满生命，但最大生命值 -1。
 */
public final class FullHealMaxHpDownRelic implements Relic {

    @Override
    public String name() {
        return "血之代价";
    }

    @Override
    public String description() {
        return "进入关卡时回满生命，但最大生命值 -1";
    }

    @Override
    public void onEnterLevel(Player player) {
        player.reduceMaxHealth(1);  // 最大生命值 -1（下限 1 点）
        player.healToFull();        // 回满血到新上限
    }
}
