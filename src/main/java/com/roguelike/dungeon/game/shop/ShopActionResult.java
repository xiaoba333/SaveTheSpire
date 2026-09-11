package com.roguelike.dungeon.game.shop;

/** 购买商品或删除卡牌的结果。 */
public enum ShopActionResult {
    SUCCESS,
    ITEM_NOT_FOUND,
    ITEM_ALREADY_SOLD,
    CARD_NOT_FOUND,
    INSUFFICIENT_GOLD,
    CARD_REMOVAL_ALREADY_USED,
    SHOP_CLOSED
}
