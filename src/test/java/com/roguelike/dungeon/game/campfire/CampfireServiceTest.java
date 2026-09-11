package com.roguelike.dungeon.game.campfire;

import com.roguelike.dungeon.flow.LevelResult;
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

class CampfireServiceTest {

    @Test
    void restShouldHealThirtyPercentAndCompleteOnlyOnce() {
        RunState state = newRunState(List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE)));
        state.getPlayer().setHealth(20);
        AtomicInteger finishCount = new AtomicInteger();
        AtomicReference<LevelResult> levelResult = new AtomicReference<>();
        CampfireService service = new CampfireService(
                state,
                result -> {
                    finishCount.incrementAndGet();
                    levelResult.set(result);
                });

        CampfireActionResult first = service.rest();
        CampfireActionResult second = service.rest();

        assertEquals(CampfireActionStatus.SUCCESS, first.status());
        assertEquals(35, state.getPlayer().getHealth());
        assertEquals(LevelResult.COMPLETED, levelResult.get());
        assertEquals(1, finishCount.get());
        assertEquals(CampfireActionStatus.CAMPFIRE_ALREADY_USED, second.status());
        assertEquals(35, state.getPlayer().getHealth());
    }

    @Test
    void fullHealthShouldMakeRestUnavailableWithoutCompleting() {
        RunState state = newRunState(List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE)));
        AtomicInteger finishCount = new AtomicInteger();
        CampfireService service = new CampfireService(
                state, result -> finishCount.incrementAndGet());

        CampfireAction rest = service.getActions().stream()
                .filter(action -> action.id().equals("rest"))
                .findFirst()
                .orElseThrow();
        CampfireActionResult result = service.rest();

        assertFalse(rest.available());
        assertEquals(CampfireActionStatus.ACTION_UNAVAILABLE, result.status());
        assertFalse(service.isUsed());
        assertEquals(0, finishCount.get());
    }

    @Test
    void smithShouldUpgradeSelectedPermanentCardAndComplete() {
        RunState state = newRunState(List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE),
                new CardInstance("defend-1", CardLibrary.DEFEND)));
        AtomicInteger finishCount = new AtomicInteger();
        CampfireService service = new CampfireService(
                state, result -> finishCount.incrementAndGet());

        CampfireActionResult result = service.smith("defend-1");

        assertTrue(result.succeeded());
        assertTrue(state.getDeck().stream()
                .filter(card -> card.id().equals("defend-1"))
                .findFirst()
                .orElseThrow()
                .upgraded());
        assertFalse(state.getDeck().stream()
                .filter(card -> card.id().equals("strike-1"))
                .findFirst()
                .orElseThrow()
                .upgraded());
        assertEquals(1, finishCount.get());
    }

    @Test
    void invalidOrNonUpgradableCardShouldNotMutateOrComplete() {
        CardInstance forge = new CardInstance("forge-1", CardLibrary.FORGE);
        RunState state = newRunState(List.of(forge));
        AtomicInteger finishCount = new AtomicInteger();
        CampfireService service = new CampfireService(
                state, result -> finishCount.incrementAndGet());

        assertEquals(CampfireActionStatus.CARD_NOT_FOUND,
                service.smith("missing").status());
        assertEquals(CampfireActionStatus.CARD_NOT_UPGRADABLE,
                service.smith(forge.id()).status());

        assertEquals(List.of(forge), state.getDeck());
        assertTrue(service.getUpgradeableCards().isEmpty());
        assertFalse(service.isUsed());
        assertEquals(0, finishCount.get());
    }

    @Test
    void upgradedCardShouldNotBeOfferedForSmithing() {
        CardInstance upgradedStrike = new CardInstance(
                "strike-1", CardLibrary.STRIKE, true);
        RunState state = newRunState(List.of(upgradedStrike));
        CampfireService service = new CampfireService(state, result -> { });

        assertTrue(service.getUpgradeableCards().isEmpty());
        assertEquals(CampfireActionStatus.CARD_NOT_UPGRADABLE,
                service.smith(upgradedStrike.id()).status());
        assertEquals(List.of(upgradedStrike), state.getDeck());
    }

    @Test
    void leaveShouldCompleteWithoutChangingPlayerOrDeck() {
        RunState state = newRunState(List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE)));
        List<CardInstance> originalDeck = state.getDeck();
        AtomicInteger finishCount = new AtomicInteger();
        CampfireService service = new CampfireService(
                state, result -> finishCount.incrementAndGet());

        CampfireActionResult result = service.leave();

        assertTrue(result.succeeded());
        assertEquals(50, state.getPlayer().getHealth());
        assertEquals(originalDeck, state.getDeck());
        assertEquals(1, finishCount.get());
    }

    private static RunState newRunState(List<CardInstance> deck) {
        return new RunState(new Player(50, 3), deck, 0, 12345L, 1);
    }
}
