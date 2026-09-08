package com.roguelike.dungeon.game.card;

/**
 * 卡牌大类，参考《杀戮尖塔》的卡牌分类。
 */
public enum CardType {
    ATTACK("攻击"),
    SKILL("技能"),
    POWER("能力"),
    STATUS("状态"),
    CURSE("诅咒");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
