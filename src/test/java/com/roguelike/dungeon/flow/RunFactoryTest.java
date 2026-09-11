package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunFactoryTest {

    private final GameCharacterCatalog catalog = new GameCharacterCatalog();

    @Test
    void createRunShouldBuildPlayerAndDeckFromCharacter() {
        RunState runState = RunFactory.createRun(catalog, "blood", 12345L, 1);

        assertEquals(10, runState.getPlayer().getMaxHealth());
        assertEquals(10, runState.getPlayer().getHealth());
        assertEquals(3, runState.getPlayer().getMaxEnergy());
        assertEquals(0, runState.getGold());
        assertEquals(10, runState.getDeck().size());
        assertEquals(12345L, runState.getRunSeed());
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
        assertEquals(1, runState.getDeck().size());
        assertEquals("descend", runState.getDeck().getFirst().card().id());
        assertEquals("降神", runState.getDeck().getFirst().card().name());
    }
}
