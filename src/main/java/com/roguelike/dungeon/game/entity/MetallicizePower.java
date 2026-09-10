package com.roguelike.dungeon.game.entity;

/**
 * 能力「金属化」：玩家每回合开始获得若干点护甲。
 */
public final class MetallicizePower implements Power {

    private final int amount;

    /**
     * @param amount 每回合开始获得的护甲值
     */
    public MetallicizePower(int amount) {
        this.amount = amount;
    }

    @Override
    public String name() {
        return "金属化";
    }

    @Override
    public String description() {
        return "每回合开始获得 " + amount + " 点护甲。";
    }

    @Override
    public void onTurnStart(Player player) {
        player.addArmor(amount);
    }
}
