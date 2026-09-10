package com.roguelike.dungeon.game.shop;

import com.roguelike.dungeon.game.card.Card;

import java.util.Objects;

/**
 * 商店中一个可购买的卡牌商品。
 *
 * @param id 商品在当前商店中的唯一编号
 * @param card 出售的卡牌定义
 * @param price 金币价格
 */
public record ShopItem(String id, Card card, int price) {

    public ShopItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("商品编号不能为空");
        }
        card = Objects.requireNonNull(card, "商品卡牌不能为 null");
        if (price < 0) {
            throw new IllegalArgumentException("商品价格不能为负数");
        }
    }
}
