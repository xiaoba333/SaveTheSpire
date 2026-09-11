package com.roguelike.dungeon.game.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardLibraryTest {

    @Test
    void startingDeckMatchesWarriorDesign() {
        List<Card> deck = CardLibrary.startingDeck();

        assertEquals(9, deck.size());
        assertEquals(4, deck.stream().filter(card -> card == CardLibrary.STRIKE).count());
        assertEquals(4, deck.stream().filter(card -> card == CardLibrary.DEFEND).count());
        assertEquals(1, deck.stream().filter(card -> card == CardLibrary.BASH).count());
        assertFalse(deck.contains(CardLibrary.FEAST));
        assertFalse(deck.contains(CardLibrary.SACRIFICE_STRIKE));
    }

    @Test
    void warriorRewardPoolExcludesBasicsAndIncludesForgeWhenMerged() {
        assertFalse(CardLibrary.warriorRewardCards().contains(CardLibrary.STRIKE));
        assertFalse(CardLibrary.warriorRewardCards().contains(CardLibrary.FORGE));
        assertTrue(CardLibrary.rewardPoolFor(CardLibrary.warriorRewardCardIds())
                .contains(CardLibrary.FORGE));
        assertTrue(CardLibrary.rewardPoolFor(CardLibrary.warriorRewardCardIds())
                .contains(CardLibrary.IRON_WAVE));
    }

    @Test
    void bloodLordRewardPoolKeepsFeastAndBloodDevotion() {
        List<Card> pool = CardLibrary.bloodLordRewardCards();

        assertTrue(pool.contains(CardLibrary.FEAST));
        assertTrue(pool.contains(CardLibrary.SACRIFICE_STRIKE));
        assertFalse(pool.contains(CardLibrary.FORGE));
        assertTrue(CardLibrary.rewardPoolFor(CardLibrary.bloodLordRewardCardIds())
                .contains(CardLibrary.FORGE));
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
