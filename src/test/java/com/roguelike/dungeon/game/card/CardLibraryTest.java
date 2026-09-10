package com.roguelike.dungeon.game.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardLibraryTest {

    @Test
    void startingDeckHasTenBasicCardsAndForge() {
        List<Card> deck = CardLibrary.startingDeck();

        assertEquals(11, deck.size());
        assertEquals(5, deck.stream().filter(card -> card.type() == CardType.ATTACK).count());
        assertEquals(6, deck.stream().filter(card -> card.type() == CardType.SKILL).count());
        assertTrue(deck.stream().allMatch(card -> card.cost() == 1));
        assertFalse(deck.stream().anyMatch(Card::exhausts));
        assertTrue(deck.contains(CardLibrary.FORGE));
    }

    @Test
    void cardDefinitionsExposePlayRules() {
        Card bloodletting = CardLibrary.byId("bloodletting");

        assertEquals(0, bloodletting.cost());
        assertTrue(bloodletting.playable());
        assertFalse(bloodletting.exhausts());
    }
}
