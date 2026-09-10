package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCardEffectContextTest {

    @Test
    void strikeEffectShouldDamageMonsterWithoutCombat() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, 1.0);

        CardLibrary.STRIKE.effect().apply(context);

        assertEquals(24, state.getMonsterHp());
    }

    @Test
    void defendEffectShouldAddPlayerArmor() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, 1.0);

        CardLibrary.DEFEND.effect().apply(context);

        assertEquals(5, state.getPlayer().getArmor());
    }

    @Test
    void upgradedMultiplierShouldScaleMonsterDamage() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, 1.25);

        context.dealDamageToMonster(8);

        assertEquals(20, state.getMonsterHp());
    }

    @Test
    void selfDamageShouldIgnoreUpgradeMultiplier() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, 1.25);

        context.dealDamageToPlayer(3);

        assertEquals(47, state.getPlayer().getHealth());
    }

    @Test
    void upgradeCardShouldNotifyHandler() {
        BattleState state = readyState();
        List<CardInstance> upgraded = new ArrayList<>();
        CombatCardEffectContext context = new CombatCardEffectContext(
                state, line -> { }, upgraded::add, 1.0);

        assertTrue(context.upgradeCard(0));
        assertEquals(1, upgraded.size());
        assertTrue(upgraded.getFirst().upgraded());
    }

    private static BattleState readyState() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = List.of(
                new CardInstance("s1", CardLibrary.STRIKE),
                new CardInstance("d1", CardLibrary.DEFEND));
        CardPiles piles = new CardPiles(line -> { });
        piles.initializeInstances(deck);
        piles.drawToHandSize(2);
        BattleState state = new BattleState(player, deck, piles, 30);
        state.setPlayerTurn(true);
        return state;
    }

    private static CombatCardEffectContext newContext(BattleState state, double multiplier) {
        return new CombatCardEffectContext(state, line -> { }, card -> { }, multiplier);
    }
}
