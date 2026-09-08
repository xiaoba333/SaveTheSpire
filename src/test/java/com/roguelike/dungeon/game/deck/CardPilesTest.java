package com.roguelike.dungeon.game.deck;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardLibrary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CardPilesTest {

    @Test
    void drawDiscardsAndReshufflesDiscardPile() {
        List<String> logs = new ArrayList<>();
        CardPiles piles = new CardPiles(logs::add, new Random(1));
        piles.initialize(List.of(
                CardLibrary.STRIKE,
                CardLibrary.DEFEND,
                CardLibrary.BASH));

        assertEquals(2, piles.drawToHandSize(2).size());
        assertEquals(2, piles.getHandSize());
        assertEquals(1, piles.getDrawPileSize());

        piles.discardHand();
        assertEquals(0, piles.getHandSize());
        assertEquals(2, piles.getDiscardPileSize());

        piles.draw(5);

        assertEquals(3, piles.getHandSize());
        assertEquals(0, piles.getDrawPileSize());
        assertEquals(0, piles.getDiscardPileSize());
        assertTrue(logs.stream().anyMatch(line -> line.contains("洗回")));
    }

    @Test
    void sendsExhaustedCardsToSeparatePile() {
        List<String> logs = new ArrayList<>();
        CardPiles piles = new CardPiles(logs::add, new Random(1));
        piles.initialize(List.of(CardLibrary.STRIKE));
        piles.draw(1);

        Card played = piles.removeFromHand(0);
        piles.sendToExhaust(played);

        assertEquals(0, piles.getHandSize());
        assertEquals(0, piles.getDrawPileSize());
        assertEquals(0, piles.getDiscardPileSize());
        assertEquals(1, piles.getExhaustPileSize());
    }
}
