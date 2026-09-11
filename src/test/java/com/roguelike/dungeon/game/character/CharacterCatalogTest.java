package com.roguelike.dungeon.game.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.RelicLibrary;

import java.util.List;

import org.junit.jupiter.api.Test;

class CharacterCatalogTest {

    private final CharacterCatalog catalog = new GameCharacterCatalog();

    @Test
    void catalogExposesWarriorAndBloodCharacters() {
        List<CharacterDefinition> characters = catalog.getAvailableCharacters();

        assertEquals(2, characters.size());
        assertEquals("warrior", characters.getFirst().id());
        assertEquals("blood", characters.get(1).id());
    }

    @Test
    void warriorCharacterHasStartingDeckPoolAndRelic() {
        CharacterDefinition character = catalog.getById("warrior");

        assertEquals("warrior", character.id());
        assertEquals("铁血战士", character.name());
        assertEquals(50, character.maxHealth());
        assertEquals(3, character.maxEnergy());
        assertEquals(0, character.startingGold());
        assertEquals(9, character.startingCardIds().size());
        assertEquals(4, character.startingCardIds().stream()
                .filter(id -> id.equals(CardLibrary.STRIKE.id())).count());
        assertEquals(4, character.startingCardIds().stream()
                .filter(id -> id.equals(CardLibrary.DEFEND.id())).count());
        assertEquals(1, character.startingCardIds().stream()
                .filter(id -> id.equals(CardLibrary.BASH.id())).count());
        assertEquals(CardLibrary.warriorRewardCardIds(), character.rewardCardIds());
        assertEquals(RelicLibrary.BURNING_BLOOD, character.startingRelicId());
    }

    @Test
    void bloodPriceCharacterHasContractFields() {
        CharacterDefinition character = catalog.getById("blood");

        assertEquals("blood", character.id());
        assertEquals("血祭者", character.name());
        assertEquals(30, character.maxHealth());
        assertEquals(3, character.maxEnergy());
        assertEquals(0, character.startingGold());
        assertEquals(10, character.startingCardIds().size());
        assertTrue(character.startingCardIds().contains(CardLibrary.FEAST.id()));
        assertTrue(character.startingCardIds().contains(CardLibrary.SACRIFICE_STRIKE.id()));
        assertEquals(CardLibrary.bloodLordRewardCardIds(), character.rewardCardIds());
        assertTrue(character.startingRelicId().isBlank());
    }

    @Test
    void startingCardIdsAreResolvableInCardLibrary() {
        for (CharacterDefinition character : catalog.getAvailableCharacters()) {
            for (String cardId : character.startingCardIds()) {
                assertEquals(cardId, CardLibrary.byId(cardId).id());
            }
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
        assertEquals(999, god.startingGold());
        assertEquals(List.of(CardLibrary.DESCEND.id()), god.startingCardIds());
        assertEquals("descend", CardLibrary.byId(god.startingCardIds().getFirst()).id());
        assertEquals("降神", CardLibrary.DESCEND.name());
        assertEquals(1, CardLibrary.DESCEND.cost());
    }
}
