package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardUpgradeTest {

    @Test
    void cardInstanceShouldReduceCostAfterUpgrade() {
        CardInstance strike = new CardInstance("strike-1", CardLibrary.STRIKE);

        assertEquals(1, strike.effectiveCost());
        assertFalse(strike.upgraded());

        CardInstance upgraded = strike.upgradedCopy();

        assertEquals(1, upgraded.effectiveCost());
        assertTrue(upgraded.upgraded());
        assertEquals(strike.id(), upgraded.id());
    }

    @Test
    void cardPilesShouldUpgradeCardInHandAndKeepInstanceId() {
        CardPiles piles = new CardPiles(line -> { }, new Random(1));
        piles.initializeInstances(List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE),
                new CardInstance("defend-1", CardLibrary.DEFEND)));
        piles.drawToHandSize(2);

        String originalId = piles.getHand().get(0).id();
        CardInstance upgraded = piles.upgradeInHand(0);

        assertNotNull(upgraded);
        assertTrue(upgraded.upgraded());
        assertEquals(originalId, upgraded.id());
        assertEquals(upgraded, piles.getHand().get(0));
    }

    @Test
    void cardPilesShouldUpgradeSelectedCardByInstanceId() {
        CardInstance strike = new CardInstance("strike-1", CardLibrary.STRIKE);
        CardInstance defend = new CardInstance("defend-1", CardLibrary.DEFEND);
        CardPiles piles = new CardPiles(line -> { }, new Random(1));
        piles.initializeInstances(List.of(strike, defend));
        piles.drawToHandSize(2);

        CardInstance upgraded = piles.upgradeInHand("defend-1");

        assertNotNull(upgraded);
        assertEquals("defend-1", upgraded.id());
        assertTrue(upgraded.upgraded());
        assertTrue(piles.getHand().stream()
                .filter(card -> card.id().equals("defend-1"))
                .findFirst()
                .orElseThrow()
                .upgraded());
        assertFalse(piles.getHand().stream()
                .filter(card -> card.id().equals("strike-1"))
                .findFirst()
                .orElseThrow()
                .upgraded());
    }

    @Test
    void runStateShouldPersistUpgradedCard() {
        RunState state = new RunState(
                new Player(50, 3),
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                1);
        CardInstance upgraded = new CardInstance(
                "strike-1", CardLibrary.STRIKE, true);

        assertTrue(state.upgradeCard(upgraded));
        assertEquals(upgraded, state.getDeck().getFirst());
    }
}
