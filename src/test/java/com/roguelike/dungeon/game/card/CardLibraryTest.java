package com.roguelike.dungeon.game.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardLibraryTest {

    @Test
    void startingDeckMatchesBloodLordDesign() {
        List<Card> deck = CardLibrary.startingDeck();

        assertEquals(10, deck.size());
        assertEquals(6, deck.stream().filter(card -> card.type() == CardType.ATTACK).count());
        assertEquals(4, deck.stream().filter(card -> card.type() == CardType.SKILL).count());
        assertTrue(deck.stream().allMatch(card -> card.cost() == 1));
        assertFalse(deck.stream().anyMatch(Card::exhausts));
        assertTrue(deck.contains(CardLibrary.FEAST));
        assertTrue(deck.contains(CardLibrary.SACRIFICE_STRIKE));
    }

    @Test
    void cardDefinitionsExposePlayRules() {
        Card bloodletting = CardLibrary.byId("bloodletting");

        assertEquals(0, bloodletting.cost());
        assertTrue(bloodletting.playable());
        assertFalse(bloodletting.exhausts());
    }

    @Test
    void unimplementedEffectCardsShouldNotBeInStartingDeck() {
        List<Card> deck = CardLibrary.startingDeck();

        assertFalse(deck.contains(CardLibrary.BLOOD_HAPPINESS));
        assertFalse(deck.contains(CardLibrary.BLOOD_RAIN));
        assertFalse(deck.contains(CardLibrary.DUSK_VEIL));
        assertEquals("blood_rain", CardLibrary.byId("blood_rain").id());
    }
}
