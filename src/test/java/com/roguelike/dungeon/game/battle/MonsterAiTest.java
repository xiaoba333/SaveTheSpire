package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class MonsterAiTest {

    private static Combat combatWith(MonsterAi monsterAi) {
        Player player = new Player(200, 3);
        List<CardInstance> deck = IntStream.range(0, 10)
                .mapToObj(i -> new CardInstance("defend-" + i, CardLibrary.DEFEND))
                .toList();
        return new Combat(player, deck, line -> { }, result -> { }, card -> { }, monsterAi);
    }

    @Test
    void cultistRitualGrowsAttack() {
        Combat combat = combatWith(new CultistAi());

        assertEquals("邪教徒", combat.getMonsterName());
        assertEquals(48, combat.getMonsterMaxHp());
        assertEquals(6, combat.getMonsterIntentInfo().value());

        combat.endPlayerTurn();
        assertEquals(200 - 6, combat.getPlayerHp());
        assertEquals(9, combat.getMonsterIntentInfo().value());

        combat.endPlayerTurn();
        assertEquals(200 - 6 - 9, combat.getPlayerHp());
        assertEquals(12, combat.getMonsterIntentInfo().value());
    }

    @Test
    void jawWormCyclesBlockThenAttacks() {
        Combat combat = combatWith(new JawWormAi());

        assertEquals("DEFEND", combat.getMonsterIntentInfo().type());

        combat.endPlayerTurn();
        assertEquals(6, combat.getMonsterBlock());
        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());
        assertEquals(8, combat.getMonsterIntentInfo().value());

        combat.endPlayerTurn();
        assertEquals(200 - 8, combat.getPlayerHp());
        assertEquals(12, combat.getMonsterIntentInfo().value());
    }

    @Test
    void louseCurlsThenBites() {
        Combat combat = combatWith(new LouseAi());

        assertEquals("DEFEND", combat.getMonsterIntentInfo().type());

        combat.endPlayerTurn();
        assertEquals(4, combat.getMonsterBlock());

        combat.endPlayerTurn();
        assertEquals(200 - 7, combat.getPlayerHp());
    }

    @Test
    void acidSlimeAlwaysAttacks() {
        Combat combat = combatWith(new AcidSlimeAi());

        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());
        assertEquals(8, combat.getMonsterIntentInfo().value());

        combat.endPlayerTurn();
        assertEquals(200 - 8, combat.getPlayerHp());
        assertEquals(8, combat.getMonsterIntentInfo().value());
    }

    @Test
    void defaultMonsterKeepsAlternatingBehavior() {
        Combat combat = combatWith(new DefaultMonsterAi());

        assertEquals(30, combat.getMonsterMaxHp());
        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());
        assertEquals(10, combat.getMonsterIntentInfo().value());

        combat.endPlayerTurn();
        assertEquals(200 - 10, combat.getPlayerHp());
        assertEquals("DEFEND", combat.getMonsterIntentInfo().type());
    }
}
