package com.roguelike.dungeon.game.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.game.card.CardLibrary;

import java.util.List;

import org.junit.jupiter.api.Test;

class CharacterCatalogTest {

    private final CharacterCatalog catalog = new GameCharacterCatalog();

    @Test
    void catalogExposesOneCharacter() {
        List<CharacterDefinition> characters = catalog.getAvailableCharacters();

        assertEquals(1, characters.size());
        assertEquals("blood", characters.getFirst().id());
    }

    @Test
    void bloodPriceCharacterHasContractFields() {
        CharacterDefinition character = catalog.getById("blood");

        assertEquals("blood", character.id());
        assertEquals("血祭者", character.name());
        assertEquals(10, character.maxHealth());
        assertEquals(3, character.maxEnergy());
        assertEquals(0, character.startingGold());
        assertEquals(10, character.startingCardIds().size());
    }

    @Test
    void startingCardIdsAreResolvableInCardLibrary() {
        CharacterDefinition character = catalog.getById("blood");

        for (String cardId : character.startingCardIds()) {
            assertEquals(cardId, CardLibrary.byId(cardId).id());
        }
    }

    @Test
    void getByIdThrowsForUnknownId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> catalog.getById("nonexistent"));

        assertTrue(exception.getMessage().contains("nonexistent"));
    }
}
