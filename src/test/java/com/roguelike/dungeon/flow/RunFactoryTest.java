package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunFactoryTest {

    private final GameCharacterCatalog catalog = new GameCharacterCatalog();

    @Test
    void createRunShouldBuildPlayerAndDeckFromCharacter() {
        RunState runState = RunFactory.createRun(catalog, "blood", 12345L, 1);

        assertEquals(30, runState.getPlayer().getMaxHealth());
        assertEquals(30, runState.getPlayer().getHealth());
        assertEquals(3, runState.getPlayer().getMaxEnergy());
        assertEquals(0, runState.getGold());
        assertEquals(10, runState.getDeck().size());
        assertEquals(12345L, runState.getRunSeed());
        assertTrue(runState.getPlayer().getRelics().isEmpty());
        assertEquals("feast", runState.getDeck().stream()
                .map(card -> card.card().id())
                .filter(id -> id.equals("feast") || id.equals("blood_feast"))
                .findFirst()
                .orElseThrow());
    }

    @Test
    void createRunShouldBuildWarriorWithBurningBloodAndStartingDeck() {
        RunState runState = RunFactory.createRun(catalog, "warrior", 12345L, 1);

        assertEquals(50, runState.getPlayer().getMaxHealth());
        assertEquals(9, runState.getDeck().size());
        assertEquals(4, runState.getDeck().stream()
                .filter(card -> card.card().id().equals("strike")).count());
        assertEquals(4, runState.getDeck().stream()
                .filter(card -> card.card().id().equals("defend")).count());
        assertEquals(1, runState.getDeck().stream()
                .filter(card -> card.card().id().equals("bash")).count());
        assertEquals(1, runState.getPlayer().getRelics().size());
        assertEquals("燃烧之血", runState.getPlayer().getRelics().getFirst().name());
    }

    @Test
    void createRunShouldRejectUnknownCharacter() {
        assertThrows(IllegalArgumentException.class,
                () -> RunFactory.createRun(catalog, "unknown", 12345L, 1));
    }

    @Test
    void createRunShouldBuildGodCharacterWithSingleDescendCard() {
        RunState runState = RunFactory.createRun(catalog, "god", 12345L, 1);

        assertEquals(50, runState.getPlayer().getMaxHealth());
        assertEquals(999, runState.getGold());
        assertEquals(1, runState.getDeck().size());
        assertEquals("descend", runState.getDeck().getFirst().card().id());
        assertEquals("降神", runState.getDeck().getFirst().card().name());
    }
}
