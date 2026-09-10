package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleStateTest {

    @Test
    void constructorShouldCopyDeckAndInitMonsterHp() {
        Player player = new Player(50, 3);
        BattleState state = newState(player);

        assertEquals(30, state.getMonsterHp());
        assertEquals(30, state.getMonsterMaxHp());
        assertEquals(0, state.getMonsterBlock());
        assertEquals(player, state.getPlayer());
        assertEquals(1, state.getBattleDeck().size());
        assertFalse(state.isFinished());
    }

    @Test
    void applyDamageToMonsterShouldAbsorbBlockThenLoseHp() {
        BattleState state = newState(new Player(50, 3));
        state.addMonsterBlock(4);

        int hpLoss = state.applyDamage(true, 10);

        assertEquals(6, hpLoss);
        assertEquals(0, state.getMonsterBlock());
        assertEquals(24, state.getMonsterHp());
    }

    @Test
    void applyDamageToPlayerShouldUseReceiveDamage() {
        Player player = new Player(50, 3);
        player.addArmor(3);
        BattleState state = newState(player);

        int hpLoss = state.applyDamage(false, 10);

        assertEquals(7, hpLoss);
        assertEquals(43, player.getHealth());
        assertEquals(0, player.getArmor());
    }

    @Test
    void addMonsterBlockShouldIgnoreNonPositiveAmount() {
        BattleState state = newState(new Player(50, 3));

        state.addMonsterBlock(0);
        state.addMonsterBlock(-5);
        assertEquals(0, state.getMonsterBlock());

        state.addMonsterBlock(8);
        assertEquals(8, state.getMonsterBlock());
    }

    @Test
    void turnFlagsShouldBeMutableWithoutCombat() {
        BattleState state = newState(new Player(50, 3));
        state.setPlayerTurn(true);
        state.setTurnNumber(2);
        state.setFinished(true);

        assertTrue(state.isPlayerTurn());
        assertEquals(2, state.getTurnNumber());
        assertTrue(state.isFinished());
    }

    private static BattleState newState(Player player) {
        List<CardInstance> deck = List.of(new CardInstance("s1", CardLibrary.STRIKE));
        return new BattleState(player, deck, new CardPiles(line -> { }), 30);
    }
}
