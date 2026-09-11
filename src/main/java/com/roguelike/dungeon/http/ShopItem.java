package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.card.Card;

/**
 * 商店里的一件商品（MVP 固定库存）。
 *
 * @param id 商品编号（前端购买时回传）
 * @param name 显示名
 * @param kind 商品类型：CARD / POTION / SERVICE
 * @param price 价格（金币）
 * @param description 效果说明
 * @param rarity 卡牌稀有度（仅 CARD 类型；非卡牌为 null）
 * @param card 对应的卡牌定义（仅 CARD 类型；非卡牌为 null）
 */
record ShopItem(
        String id,
        String name,
        String kind,
        int price,
        String description,
        String rarity,
        Card card) {
}
