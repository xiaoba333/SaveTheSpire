package com.roguelike.dungeon.game.shop;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.run.RunState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** 管理一个商店节点的商品、购买、删卡和离店结算。 */
public final class ShopService {
    public static final int MAX_CARD_ITEMS = 5;
    public static final int CARD_PRICE = 50;
    public static final int CARD_REMOVAL_PRICE = 75;

    private final RunState runState;
    private final Map<String, ShopItem> itemsById;
    private final Set<String> soldItemIds = new LinkedHashSet<>();
    private final Supplier<String> cardInstanceIdSupplier;
    private final LevelFinishHandler finishHandler;

    private boolean cardRemovalUsed;
    private boolean closed;

    /** 根据商店种子创建一个可复现的卡牌商店。 */
    public ShopService(
            RunState runState,
            List<Card> cardPool,
            long shopSeed,
            LevelFinishHandler finishHandler) {
        this(
                runState,
                generateItems(cardPool, shopSeed),
                () -> UUID.randomUUID().toString(),
                finishHandler);
    }

    /** 供测试注入固定商品和卡牌实例编号。 */
    ShopService(
            RunState runState,
            List<ShopItem> items,
            Supplier<String> cardInstanceIdSupplier,
            LevelFinishHandler finishHandler) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.cardInstanceIdSupplier = Objects.requireNonNull(
                cardInstanceIdSupplier, "卡牌实例编号生成器不能为 null");
        this.finishHandler = Objects.requireNonNull(
                finishHandler, "关卡结束处理器不能为 null");
        this.itemsById = indexItems(items);
    }

    /** 从卡池中确定性地抽取最多五张不同定义的卡牌。 */
    public static List<ShopItem> generateItems(List<Card> cardPool, long shopSeed) {
        Objects.requireNonNull(cardPool, "商店卡池不能为 null");
        Map<String, Card> uniqueCards = new LinkedHashMap<>();
        for (Card card : cardPool) {
            Objects.requireNonNull(card, "商店卡池不能包含 null");
            if (card.id() == null || card.id().isBlank()) {
                throw new IllegalArgumentException("商店卡牌定义编号不能为空");
            }
            uniqueCards.putIfAbsent(card.id(), card);
        }

        List<Card> shuffled = new ArrayList<>(uniqueCards.values());
        Collections.shuffle(shuffled, new Random(shopSeed));
        int count = Math.min(MAX_CARD_ITEMS, shuffled.size());
        List<ShopItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new ShopItem("shop-card-" + (i + 1), shuffled.get(i), CARD_PRICE));
        }
        return List.copyOf(items);
    }

    /** 返回尚未售出的商品快照。 */
    public List<ShopItem> getAvailableItems() {
        return itemsById.values().stream()
                .filter(item -> !soldItemIds.contains(item.id()))
                .toList();
    }

    /** 返回当前可以选择删除的永久牌组快照。 */
    public List<CardInstance> getRemovableCards() {
        return runState.getDeck();
    }

    public boolean isCardRemovalUsed() {
        return cardRemovalUsed;
    }

    public boolean isClosed() {
        return closed;
    }

    /** 购买一个卡牌商品。购买成功后卡牌进入永久牌组。 */
    public ShopActionResult buy(String itemId) {
        if (closed) {
            return ShopActionResult.SHOP_CLOSED;
        }
        ShopItem item = itemsById.get(itemId);
        if (item == null) {
            return ShopActionResult.ITEM_NOT_FOUND;
        }
        if (soldItemIds.contains(itemId)) {
            return ShopActionResult.ITEM_ALREADY_SOLD;
        }
        if (runState.getGold() < item.price()) {
            return ShopActionResult.INSUFFICIENT_GOLD;
        }

        String cardInstanceId = Objects.requireNonNull(
                cardInstanceIdSupplier.get(), "生成的卡牌实例编号不能为 null");
        if (cardInstanceId.isBlank()) {
            throw new IllegalStateException("生成的卡牌实例编号不能为空");
        }

        runState.addCard(new CardInstance(cardInstanceId, item.card()));
        if (!runState.spendGold(item.price())) {
            runState.removeCard(cardInstanceId);
            return ShopActionResult.INSUFFICIENT_GOLD;
        }
        soldItemIds.add(itemId);
        return ShopActionResult.SUCCESS;
    }

    /** 花费金币删除一张永久牌组中的卡牌，每个商店只能成功一次。 */
    public ShopActionResult removeCard(String cardInstanceId) {
        if (closed) {
            return ShopActionResult.SHOP_CLOSED;
        }
        if (cardRemovalUsed) {
            return ShopActionResult.CARD_REMOVAL_ALREADY_USED;
        }
        boolean cardExists = runState.getDeck().stream()
                .anyMatch(card -> card.id().equals(cardInstanceId));
        if (!cardExists) {
            return ShopActionResult.CARD_NOT_FOUND;
        }
        if (!runState.spendGold(CARD_REMOVAL_PRICE)) {
            return ShopActionResult.INSUFFICIENT_GOLD;
        }
        if (!runState.removeCard(cardInstanceId)) {
            runState.addGold(CARD_REMOVAL_PRICE);
            return ShopActionResult.CARD_NOT_FOUND;
        }
        cardRemovalUsed = true;
        return ShopActionResult.SUCCESS;
    }

    /**
     * 离开商店并完成当前地图节点。
     *
     * @return 首次离开返回 true；商店已关闭时返回 false
     */
    public boolean leave() {
        if (closed) {
            return false;
        }
        closed = true;
        finishHandler.onLevelFinished(LevelResult.COMPLETED);
        return true;
    }

    private static Map<String, ShopItem> indexItems(List<ShopItem> items) {
        Objects.requireNonNull(items, "商店商品不能为 null");
        Map<String, ShopItem> indexed = new LinkedHashMap<>();
        for (ShopItem item : items) {
            Objects.requireNonNull(item, "商店商品不能包含 null");
            if (indexed.put(item.id(), item) != null) {
                throw new IllegalArgumentException("商店商品编号重复: " + item.id());
            }
        }
        return indexed;
    }
}
