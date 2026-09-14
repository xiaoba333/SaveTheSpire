package com.roguelike.dungeon.game.shop;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.entity.Relic;

import java.util.Objects;

/**
 * 商店中一个可购买的商品：卡牌或遗物，二者只居其一。
 *
 * @param id 商品在当前商店中的唯一编号
 * @param card 出售的卡牌定义；遗物商品为 {@code null}
 * @param relic 出售的遗物；卡牌商品为 {@code null}
 * @param price 金币价格
 */
public record ShopItem(String id, Card card, Relic relic, int price) {

    public ShopItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("商品编号不能为空");
        }
        if (price < 0) {
            throw new IllegalArgumentException("商品价格不能为负数");
        }
        if ((card == null) == (relic == null)) {
            throw new IllegalArgumentException("商品必须是卡牌或遗物其中一种");
        }
    }

    /** 卡牌商品。 */
    public ShopItem(String id, Card card, int price) {
        this(id, Objects.requireNonNull(card, "商品卡牌不能为 null"), null, price);
    }

    /** 遗物商品。 */
    public static ShopItem ofRelic(String id, Relic relic, int price) {
        return new ShopItem(id, null, relic, price);
    }

    public boolean isRelic() {
        return relic != null;
    }

    public String displayName() {
        return isRelic() ? relic.name() : card.name();
    }

    public String displayDescription() {
        return isRelic() ? relic.description() : card.description();
    }

    public String displayLabel() {
        return isRelic()
                ? relic.name() + "（遗物）"
                : card.label();
    }
}
