package com.roguelike.dungeon.game.card;

/**
 * 手牌、抽牌堆、弃牌堆或消耗堆中的一张具体卡牌。
 *
 * <p>同一个卡牌定义可能在一副牌中出现多张，因此用 {@code id} 区分实例。</p>
 *
 * <p>{@code upgraded} 表示这张牌实例是否已经升级。升级状态属于牌实例，
 * 不属于 Card 模板，因此同名卡牌可以有一部分升级、一部分未升级。</p>
 */
public record CardInstance(String id, Card card, boolean upgraded) {

    /**
     * 兼容旧调用：默认创建未升级牌实例。
     *
     * @param id 牌实例唯一编号
     * @param card 卡牌模板
     */
    public CardInstance(String id, Card card) {
        this(id, card, false);
    }

    /**
     * 返回升级后的牌实例，保留原实例编号。
     *
     * @return 相同 id、相同 Card 模板、upgraded=true 的新实例
     */
    public CardInstance upgradedCopy() {
        return new CardInstance(id, card, true);
    }

    /**
     * 计算实际能量费用。
     *
     * <p>升级牌正费用统一减 1，保证升级后一定降低；0 费牌仍为 0。
     * 但如果卡牌定义了显式升级效果（{@link Card#upgradedEffect()}），
     * 升级只改变效果、不降低费用。</p>
     *
     * @return 实际需要消耗的能量
     */
    public int effectiveCost() {
        if (!upgraded || !card.upgradable()) {
            return card.cost();
        }
        if (card.upgradedEffect() != null) {
            return card.cost();
        }
        return Math.max(0, card.cost() - 1);
    }
}
