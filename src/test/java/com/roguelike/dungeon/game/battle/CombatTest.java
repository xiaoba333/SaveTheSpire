package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatTest {

    @Test
    void playingCardConsumesEnergyAndMovesCardToDiscard() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertEquals(5, combat.getDrawPileSize());
        assertEquals(0, combat.getDiscardPileSize());

        combat.playCard(0);

        assertEquals(2, combat.getEnergy());
        assertEquals(4, combat.getHand().size());
        assertEquals(1, combat.getDiscardPileSize());
        assertTrue(logs.stream().anyMatch(line -> line.contains("消耗 1 点能量")));
    }

    @Test
    void cardCannotBePlayedWithoutEnoughEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);

        int handSize = combat.getHand().size();
        int discardSize = combat.getDiscardPileSize();
        int energy = combat.getEnergy();

        combat.playCard(0);

        assertEquals(0, energy);
        assertEquals(handSize, combat.getHand().size());
        assertEquals(discardSize, combat.getDiscardPileSize());
        assertTrue(logs.stream().anyMatch(line -> line.contains("能量不足")));
    }

    @Test
    void endingTurnDiscardsHandAndRefreshesEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);
        combat.endPlayerTurn();

        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertTrue(combat.isPlayerTurn());
    }
}
