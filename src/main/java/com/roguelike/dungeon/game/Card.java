package com.roguelike.dungeon.game;

/**
 * 手牌中的一张具体卡牌。
 */
public record Card(CardType type) {

    /** 按钮上显示的文字，例如「攻击 6」。 */
    public String label() {
        return type.displayName() + " " + type.value();
    }
}
