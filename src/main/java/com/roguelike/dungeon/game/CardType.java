package com.roguelike.dungeon.game;

/**
 * 本局仅有的两种卡牌：攻击与防御。
 */
public enum CardType {
    ATTACK("攻击", 6),
    DEFEND("防御", 6);

    private final String displayName;
    private final int value;

    CardType(String displayName, int value) {
        this.displayName = displayName;
        this.value = value;
    }

    public String displayName() {
        return displayName;
    }

    public int value() {
        return value;
    }
}
