package com.roguelike.dungeon.game.card;

/**
 * 手牌、抽牌堆、弃牌堆或消耗堆中的一张具体卡牌。
 *
 * <p>同一个卡牌定义可能在一副牌中出现多张，因此用 {@code id} 区分实例。</p>
 */
public record CardInstance(String id, Card card) {
}
