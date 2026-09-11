package com.roguelike.dungeon.game.shop;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopServiceTest {
    private static final List<Card> CARD_POOL = List.of(
            CardLibrary.STRIKE,
            CardLibrary.DEFEND,
            CardLibrary.BASH,
            CardLibrary.QUICK_SLASH,
            CardLibrary.HEAVY_STRIKE,
            CardLibrary.IRON_WAVE);

    @Test
    void sameSeedShouldGenerateSameFiveUniqueItems() {
        List<ShopItem> first = ShopService.generateItems(CARD_POOL, 12345L);
        List<ShopItem> second = ShopService.generateItems(CARD_POOL, 12345L);

        assertEquals(first, second);
        assertEquals(ShopService.MAX_CARD_ITEMS, first.size());
        assertEquals(first.size(), first.stream().map(item -> item.card().id()).distinct().count());
        assertTrue(first.stream().allMatch(item -> item.price() == ShopService.CARD_PRICE));
    }

    @Test
    void buyingCardShouldDeductGoldAddCardAndRemoveItemFromStock() {
        RunState state = newRunState(100);
        ShopItem item = new ShopItem("item-1", CardLibrary.BASH, 50);
        ShopService service = new ShopService(
                state, List.of(item), () -> "bought-card-1", result -> { });

        ShopActionResult result = service.buy(item.id());

        assertEquals(ShopActionResult.SUCCESS, result);
        assertEquals(50, state.getGold());
        assertTrue(state.getDeck().contains(
                new CardInstance("bought-card-1", CardLibrary.BASH)));
        assertTrue(service.getAvailableItems().isEmpty());
        assertEquals(ShopActionResult.ITEM_ALREADY_SOLD, service.buy(item.id()));
        assertEquals(50, state.getGold());
    }

    @Test
    void failedPurchaseShouldNotChangeGoldOrDeck() {
        RunState state = newRunState(20);
        ShopService service = new ShopService(
                state,
                List.of(new ShopItem("item-1", CardLibrary.BASH, 50)),
                () -> "unused-id",
                result -> { });
        List<CardInstance> originalDeck = state.getDeck();

        assertEquals(ShopActionResult.INSUFFICIENT_GOLD, service.buy("item-1"));
        assertEquals(ShopActionResult.ITEM_NOT_FOUND, service.buy("missing"));

        assertEquals(20, state.getGold());
        assertEquals(originalDeck, state.getDeck());
        assertEquals(1, service.getAvailableItems().size());
    }

    @Test
    void cardRemovalShouldCostGoldAndOnlySucceedOncePerShop() {
        RunState state = newRunState(100);
        ShopService service = new ShopService(
                state, List.of(), () -> "unused-id", result -> { });

        assertEquals(ShopActionResult.SUCCESS, service.removeCard("strike-1"));

        assertEquals(25, state.getGold());
        assertFalse(state.getDeck().stream().anyMatch(card -> card.id().equals("strike-1")));
        assertTrue(service.isCardRemovalUsed());
        assertEquals(ShopActionResult.CARD_REMOVAL_ALREADY_USED,
                service.removeCard("defend-1"));
        assertEquals(25, state.getGold());
        assertEquals(1, state.getDeck().size());
    }

    @Test
    void invalidOrUnaffordableRemovalShouldNotChangeState() {
        RunState state = newRunState(50);
        ShopService service = new ShopService(
                state, List.of(), () -> "unused-id", result -> { });
        List<CardInstance> originalDeck = state.getDeck();

        assertEquals(ShopActionResult.CARD_NOT_FOUND, service.removeCard("missing"));
        assertEquals(ShopActionResult.INSUFFICIENT_GOLD, service.removeCard("strike-1"));

        assertEquals(50, state.getGold());
        assertEquals(originalDeck, state.getDeck());
        assertFalse(service.isCardRemovalUsed());
    }

    @Test
    void leavingShouldNotifyOnceAndCloseShop() {
        RunState state = newRunState(100);
        AtomicInteger notificationCount = new AtomicInteger();
        AtomicReference<LevelResult> result = new AtomicReference<>();
        ShopService service = new ShopService(
                state,
                List.of(new ShopItem("item-1", CardLibrary.BASH, 50)),
                () -> "unused-id",
                levelResult -> {
                    result.set(levelResult);
                    notificationCount.incrementAndGet();
                });

        assertTrue(service.leave());
        assertFalse(service.leave());

        assertTrue(service.isClosed());
        assertEquals(1, notificationCount.get());
        assertEquals(LevelResult.COMPLETED, result.get());
        assertEquals(ShopActionResult.SHOP_CLOSED, service.buy("item-1"));
        assertEquals(ShopActionResult.SHOP_CLOSED, service.removeCard("strike-1"));
    }

    private static RunState newRunState(int gold) {
        return new RunState(
                new Player(50, 3),
                List.of(
                        new CardInstance("strike-1", CardLibrary.STRIKE),
                        new CardInstance("defend-1", CardLibrary.DEFEND)),
                gold,
                12345L,
                1);
    }
}
