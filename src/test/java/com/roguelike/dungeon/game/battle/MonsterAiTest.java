package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonsterAiTest {

    @BeforeAll
    static void initBestiary() {
        ActOneBestiary.init();
    }

    private static Combat combatWith(MonsterAi monsterAi) {
        Player player = new Player(200, 3);
        List<CardInstance> deck = IntStream.range(0, 10)
                .mapToObj(i -> new CardInstance("defend-" + i, CardLibrary.DEFEND))
                .toList();
        return new Combat(player, deck, line -> { }, result -> { }, card -> { }, monsterAi);
    }

    @Test
    void skeletonAttacksThenHeals() {
        Combat combat = combatWith(ScriptedMonsterAi.of("skeleton"));
        assertEquals("骷髅", combat.getMonsterName());
        assertEquals(35, combat.getMonsterMaxHp());
        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());

        int hp = combat.getPlayerHp();
        combat.endPlayerTurn();
        assertTrue(combat.getPlayerHp() < hp);
        assertEquals("HEAL", combat.getMonsterIntentInfo().type());
    }

    @Test
    void grubIsANormalMonster() {
        Combat combat = combatWith(ScriptedMonsterAi.of("grub"));
        assertEquals("蛆", combat.getMonsterName());
        assertEquals(12, combat.getMonsterMaxHp());
    }

    @Test
    void wraithOpensWithDebuff() {
        Combat combat = combatWith(ScriptedMonsterAi.of("wraith"));
        assertEquals("亡灵", combat.getMonsterName());
        assertEquals(20, combat.getMonsterMaxHp());
    }

    @Test
    void explorerFemaleIsSelectable() {
        Combat combat = combatWith(ScriptedMonsterAi.of("explorer_female"));
        assertEquals("探险者女", combat.getMonsterName());
        assertEquals(20, combat.getMonsterMaxHp());
    }

    @Test
    void eliteIsGiantRemains() {
        Combat combat = combatWith(MonsterCatalog.elite());
        assertEquals("巨人遗骸", combat.getMonsterName());
        assertEquals(75, combat.getMonsterMaxHp());
    }

    @Test
    void bossStartsAsKairosEgg() {
        Combat combat = combatWith(MonsterCatalog.boss());
        assertTrue(combat.getMonsterName().contains("凯洛斯的蛋"));
        assertEquals(10, combat.getMonsterMaxHp());
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
