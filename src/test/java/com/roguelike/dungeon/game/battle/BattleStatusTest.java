package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.StatusEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleStatusTest {

    @Test
    void vulnerableMonsterTakesBonusDamage() {
        BattleState state = newState(new Player(50, 3));
        state.getMonster().addStacks(StatusEffect.VULNERABLE, 1);

        state.applyDamage(true, 6);  // 6 * 1.5 = 9

        assertEquals(21, state.getMonsterHp());  // 30 - 9
    }

    @Test
    void weakPlayerDealsLessDamage() {
        Player player = new Player(50, 3);
        player.addStacks(StatusEffect.WEAK, 1);
        BattleState state = newState(player);

        state.applyDamage(true, 8);  // 8 * 0.75 = 6

        assertEquals(24, state.getMonsterHp());  // 30 - 6
    }

    @Test
    void vulnerablePlayerTakesBonusDamage() {
        Player player = new Player(50, 3);
        player.addStacks(StatusEffect.VULNERABLE, 1);
        BattleState state = newState(player);

        state.applyDamage(false, 10);  // 10 * 1.5 = 15

        assertEquals(35, player.getHealth());  // 50 - 15
    }

    @Test
    void monsterPoisonTicksAtEndOfTurn() {
        BattleState state = newState(new Player(50, 3));
        state.getMonster().addStacks(StatusEffect.POISON, 3);

        state.getMonster().tickEndOfTurn();

        assertEquals(27, state.getMonsterHp());  // 30 - 3
        assertEquals(2, state.getMonster().getStacks(StatusEffect.POISON));
    }

    @Test
    void playerPoisonTicksWhenEndingTurn() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = List.of(new CardInstance("d-1", CardLibrary.DEFEND));
        Combat combat = new Combat(
                player, deck, line -> { }, result -> { }, card -> { },
                new DefaultMonsterAi("木桩", 100, 0, 0));
        // 战斗开始会清空状态，所以中毒在开战后再上。
        player.addStacks(StatusEffect.POISON, 3);

        combat.endPlayerTurn();

        assertEquals(47, combat.getPlayerHp());  // 50 - 3（木桩攻击 0）
        assertEquals(2, player.getStacks(StatusEffect.POISON));
    }

    private static BattleState newState(Player player) {
        List<CardInstance> deck = List.of(new CardInstance("s1", CardLibrary.STRIKE));
        return new BattleState(player, deck, new CardPiles(line -> { }), 30);
    }
}
