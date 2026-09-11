package com.roguelike.dungeon.game.card;

/**
 * 卡牌稀有度。
 *
 * <p>文档中的 1 / 2 / 3 分别对应普通 / 罕见 / 稀有。</p>
 */
public enum CardRarity {
    COMMON("普通"),
    UNCOMMON("罕见"),
    RARE("稀有");

    private final String displayName;

    CardRarity(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
