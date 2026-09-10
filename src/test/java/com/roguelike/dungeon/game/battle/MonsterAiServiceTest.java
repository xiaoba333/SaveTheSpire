package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterAiServiceTest {

    @Test
    void regularMonsterShouldAlternateAttackThenDefend() {
        BattleState state = newState(new Player(50, 3));
        state.setMonsterWillAttack(true);
        MonsterAiService ai = MonsterAiService.regular();

        MonsterAi.MonsterTurnResult first = ai.takeTurn(state);
        assertTrue(first.attacked());
        assertEquals(10, first.value());
        assertEquals(40, state.getPlayer().getHealth());
        assertEquals(0, state.getMonsterBlock());
        assertFalse(state.isMonsterWillAttack());
        assertFalse(state.isPlayerTurn());

        MonsterAi.MonsterTurnResult second = ai.takeTurn(state);
        assertFalse(second.attacked());
        assertEquals(10, second.value());
        assertEquals(10, state.getMonsterBlock());
        assertTrue(state.isMonsterWillAttack());
    }

    @Test
    void bossFactoryShouldUseSameDefaultNumbersAsRegular() {
        MonsterAiService boss = MonsterAiService.boss();
        assertEquals(MonsterAiService.regular().getAttackDamage(), boss.getAttackDamage());
        assertEquals(MonsterAiService.regular().getBlockAmount(), boss.getBlockAmount());
    }

    @Test
    void customBossCanUseDifferentAttackValue() {
        BattleState state = newState(new Player(50, 3));
        state.setMonsterWillAttack(true);
        MonsterAiService boss = new MonsterAiService(20, 15);

        MonsterAi.MonsterTurnResult result = boss.takeTurn(state);

        assertTrue(result.attacked());
        assertEquals(20, result.value());
        assertEquals(30, state.getPlayer().getHealth());
        assertEquals("DEFEND", boss.intentInfo(state).type());
        assertEquals(15, boss.intentInfo(state).value());
    }

    @Test
    void intentShouldBeNullWhenBattleFinished() {
        BattleState state = newState(new Player(50, 3));
        state.setFinished(true);
        MonsterAiService ai = MonsterAiService.regular();

        assertEquals("已倒下", ai.intentText(state));
        assertNull(ai.intentInfo(state));
    }

    private static BattleState newState(Player player) {
        List<CardInstance> deck = List.of(new CardInstance("s1", CardLibrary.STRIKE));
        return new BattleState(player, deck, new CardPiles(line -> { }), 30);
    }
}
