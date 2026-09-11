package com.roguelike.dungeon.game.card;

/**
 * 一张卡牌的不可变定义。
 *
 * <p>卡牌实例会在牌堆、手牌、弃牌堆之间移动，因此这里不保存任何战斗状态，
 * 实际效果由 {@link CardEffect} 在打出时执行。</p>
 */
public record Card(
        String id,
        String name,
        CardType type,
        int cost,
        String description,
        String upgradedDescription,
        CardEffect effect,
        boolean exhausts,
        boolean playable,
        boolean upgradable) {

    public Card {
        if (cost < 0) {
            throw new IllegalArgumentException("卡牌费用不能为负数");
        }
    }

    /**
     * 兼容旧调用：升级说明默认与普通说明相同。
     */
    public Card(
            String id,
            String name,
            CardType type,
            int cost,
            String description,
            CardEffect effect,
            boolean exhausts,
            boolean playable,
            boolean upgradable) {
        this(
                id,
                name,
                type,
                cost,
                description,
                description,
                effect,
                exhausts,
                playable,
                upgradable);
    }

    /**
     * 按钮上显示的文字，例如「1费 打击」。
     */
    public String label() {
        return cost + "费 " + name;
    }
}
