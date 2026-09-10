package com.roguelike.dungeon.game.run;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunStateTest {

    @Test
    void newRunShouldKeepInitialSharedState() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = List.of(
                new CardInstance("strike-1", CardLibrary.STRIKE));

        RunState state = new RunState(player, deck, 99, 12345L, 1);

        assertSame(player, state.getPlayer());
        assertEquals(deck, state.getDeck());
        assertEquals(99, state.getGold());
        assertEquals(1, state.getCurrentAct());
        assertEquals(1, state.getTotalActs());
        assertFalse(state.hasNextAct());
        assertFalse(state.getMapService().getAvailableNodes().isEmpty());
    }

    @Test
    void playerChangesShouldBeVisibleThroughSameRunState() {
        Player player = new Player(50, 3);
        RunState state = new RunState(player, List.of(), 0, 12345L, 1);

        player.takeDamage(12);

        assertSame(player, state.getPlayer());
        assertEquals(38, state.getPlayer().getHealth());
    }

    @Test
    void spendingGoldShouldBeAtomic() {
        RunState state = new RunState(
                new Player(50, 3), List.of(), 100, 12345L, 1);

        assertTrue(state.spendGold(40));
        assertEquals(60, state.getGold());

        assertFalse(state.spendGold(80));
        assertEquals(60, state.getGold());

        state.addGold(25);
        assertEquals(85, state.getGold());
    }

    @Test
    void deckSnapshotShouldNotAllowExternalModification() {
        CardInstance strike = new CardInstance("strike-1", CardLibrary.STRIKE);
        CardInstance defend = new CardInstance("defend-1", CardLibrary.DEFEND);
        RunState state = new RunState(
                new Player(50, 3), List.of(strike), 0, 12345L, 1);

        List<CardInstance> snapshot = state.getDeck();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(defend));

        state.addCard(defend);
        assertEquals(List.of(strike, defend), state.getDeck());
        assertTrue(state.removeCard(strike.id()));
        assertEquals(List.of(defend), state.getDeck());
    }

    @Test
    void advancingActShouldCreateNewMapUntilFinalAct() {
        RunState state = new RunState(
                new Player(50, 3), List.of(), 0, 12345L, 2);
        MapService firstActMap = state.getMapService();

        assertTrue(state.hasNextAct());
        state.advanceAct();

        assertEquals(2, state.getCurrentAct());
        assertFalse(state.hasNextAct());
        assertNotSame(firstActMap, state.getMapService());
        assertThrows(IllegalStateException.class, state::advanceAct);
    }
}
