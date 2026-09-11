package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CardEffectTest {

    private static Combat combatWith(Player player, List<CardInstance> deck, MonsterAi monsterAi) {
        return new Combat(player, deck, line -> { }, result -> { }, card -> { }, monsterAi);
    }

    @Test
    void metallicizeGrantsBlockAtStartOfNextTurn() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = IntStream.range(0, 10)
                .mapToObj(i -> new CardInstance("m-" + i, CardLibrary.BLOOD_METALLICIZE))
                .toList();
        Combat combat = combatWith(player, deck, new DefaultMonsterAi());

        combat.playCard(0);
        assertEquals(0, combat.getPlayerBlock());

        combat.endPlayerTurn();

        assertEquals(3, combat.getPlayerBlock());
    }

    @Test
    void feastKillsAndGrantsMaxHp() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = List.of(new CardInstance("f1", CardLibrary.FEAST));
        Combat combat = combatWith(player, deck, new DefaultMonsterAi("木桩", 5, 1, 0));

        combat.playCard(0);

        assertEquals(51, combat.getPlayerMaxHp());
        assertTrue(combat.isFinished());
        assertEquals("VICTORY", combat.getResult());
    }

    @Test
    void hemokinesisDealsTwoToSelfAndTwelveToMonster() {
        Player player = new Player(20, 3);
        player.setHealth(12);
        List<CardInstance> deck = List.of(new CardInstance("d1", CardLibrary.SACRIFICE_STRIKE));
        Combat combat = combatWith(player, deck, new DefaultMonsterAi("木桩", 100, 0, 0));

        combat.playCard(0);

        assertEquals(100 - 12, combat.getMonsterHp());
        assertEquals(10, combat.getPlayerHp());
    }

    @Test
    void upgradedHemokinesisDealsTwoToSelfAndSixteenToMonster() {
        CardInstance upgraded = new CardInstance("d1", CardLibrary.SACRIFICE_STRIKE, true);
        assertEquals(1, upgraded.effectiveCost());
        Player player = new Player(20, 3);
        player.setHealth(12);
        Combat combat = combatWith(
                player, List.of(upgraded), new DefaultMonsterAi("木桩", 100, 0, 0));

        combat.playCard(0);

        assertEquals(100 - 16, combat.getMonsterHp());
        assertEquals(10, combat.getPlayerHp());
    }

    @Test
    void upgradedBloodAttackDealsNineAndKeepsCost() {
        CardInstance upgraded = new CardInstance("a1", CardLibrary.STRIKE, true);
        assertEquals(1, upgraded.effectiveCost());

        Player player = new Player(50, 3);
        Combat combat = combatWith(player, List.of(upgraded), new DefaultMonsterAi("木桩", 100, 0, 0));

        combat.playCard(0);

        assertEquals(100 - 9, combat.getMonsterHp());
    }

    @Test
    void descendDealsNineHundredNinetyNine() {
        Player player = new Player(50, 3);
        Combat combat = combatWith(
                player,
                List.of(new CardInstance("g1", CardLibrary.DESCEND)),
                new DefaultMonsterAi("木桩", 100, 0, 0));

        combat.playCard(0);

        assertEquals(0, combat.getMonsterHp());
        assertTrue(combat.isFinished());
        assertEquals("VICTORY", combat.getResult());
    }
}
