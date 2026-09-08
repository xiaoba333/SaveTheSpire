package com.roguelike.dungeon.game.card;

/**
 * 卡牌效果的执行入口。
 *
 * <p>新卡牌只需要实现这个接口，不必修改战斗主流程。</p>
 */
@FunctionalInterface
public interface CardEffect {

    /**
     * 执行卡牌效果。
     *
     * @param context 战斗上下文，提供伤害、护甲、抽牌、能量等操作
     */
    void apply(CardEffectContext context);
}
