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
    void catalogExposesBothCharacters() {
        List<CharacterDefinition> characters = catalog.getAvailableCharacters();

        assertEquals(2, characters.size());
        assertEquals("blood", characters.get(0).id());
        assertEquals("warrior", characters.get(1).id());
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
    void warriorCharacterHasContractFields() {
        CharacterDefinition character = catalog.getById("warrior");

        assertEquals("warrior", character.id());
        assertEquals("战士", character.name());
        assertEquals(50, character.maxHealth());
        assertEquals(3, character.maxEnergy());
        assertEquals(150, character.startingGold());
        assertEquals(10, character.startingCardIds().size());
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

    @Test
    void hiddenGodCharacterIsUnlockableByIdButNotListed() {
        List<CharacterDefinition> characters = catalog.getAvailableCharacters();
        assertTrue(characters.stream().noneMatch(character -> "god".equals(character.id())));

        CharacterDefinition god = catalog.getById("god");
        assertEquals("god", god.id());
        assertEquals("god", god.name());
        assertEquals(List.of(CardLibrary.DESCEND.id()), god.startingCardIds());
        assertEquals("descend", CardLibrary.byId(god.startingCardIds().getFirst()).id());
        assertEquals("降神", CardLibrary.DESCEND.name());
        assertEquals(1, CardLibrary.DESCEND.cost());
    }
}
