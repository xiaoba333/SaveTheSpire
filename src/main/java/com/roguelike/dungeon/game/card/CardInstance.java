package com.roguelike.dungeon.game.card;

/**
 * 手牌、抽牌堆、弃牌堆或消耗堆中的一张具体卡牌。
 *
 * <p>同一个卡牌定义可能在一副牌中出现多张，因此用 {@code id} 区分实例。</p>
 *
 * <p>{@link Card} 是卡牌模板，例如“打击”这张牌是什么效果；{@code CardInstance}
 * 是某张具体的牌，例如手牌里第 1 张打击。即使模板相同，不同实例也有不同 id，
 * 这样才能在抽牌堆、手牌、弃牌堆之间精确移动。</p>
 *
 * <p>这也是一个 record，编译器会自动提供 {@code id()}、{@code card()}、
 * {@code equals}、{@code hashCode} 和 {@code toString}。</p>
 */
public record CardInstance(String id, Card card) {
}
