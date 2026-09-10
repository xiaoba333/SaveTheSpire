package com.roguelike.dungeon.game.character;

import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterCatalogTest {

    @Test
    void placeholderWarriorShouldUseOriginalMvpData() {
        CharacterDefinition warrior = CharacterCatalog.WARRIOR;

        assertEquals("warrior", warrior.id());
        assertEquals(50, warrior.maxHealth());
        assertEquals(3, warrior.maxEnergy());
        assertEquals(0, warrior.startingGold());
        assertEquals(CardLibrary.startingDeck(), warrior.startingDeck());
        assertEquals(warrior, CharacterCatalog.findById("warrior").orElseThrow());
    }

    @Test
    void creatingRunsShouldReturnIndependentPlayersAndDecks() {
        RunState first = CharacterCatalog.WARRIOR.createRunState(12345L, 1);
        RunState second = CharacterCatalog.WARRIOR.createRunState(12345L, 1);

        assertNotSame(first.getPlayer(), second.getPlayer());
        assertEquals(50, first.getPlayer().getHealth());
        assertEquals(3, first.getPlayer().getMaxEnergy());
        assertEquals(CardLibrary.startingDeck().size(), first.getDeck().size());
        assertEquals(first.getDeck().size(), first.getDeck().stream()
                .map(card -> card.id()).distinct().count());

        first.removeCard(first.getDeck().getFirst().id());
        assertEquals(CardLibrary.startingDeck().size() - 1, first.getDeck().size());
        assertEquals(CardLibrary.startingDeck().size(), second.getDeck().size());
        assertTrue(CharacterCatalog.findById("missing").isEmpty());
    }
}
