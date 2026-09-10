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
        CombatCardEffectContext context = newContext(state, false);

        CardLibrary.STRIKE.effect().apply(context);

        assertEquals(24, state.getMonsterHp());
    }

    @Test
    void defendEffectShouldAddPlayerArmor() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, false);

        CardLibrary.DEFEND.effect().apply(context);

        assertEquals(6, state.getPlayer().getArmor());
    }

    @Test
    void upgradedFlagShouldBeExposedToCardEffects() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, true);

        assertTrue(context.isUpgraded());
    }

    @Test
    void selfDamageShouldIgnoreUpgradeMultiplier() {
        BattleState state = readyState();
        CombatCardEffectContext context = newContext(state, false);

        context.dealDamageToPlayer(3);

        assertEquals(47, state.getPlayer().getHealth());
    }

    @Test
    void upgradeCardShouldNotifyHandler() {
        BattleState state = readyState();
        List<CardInstance> upgraded = new ArrayList<>();
        String targetId = state.getPiles().getHand().getFirst().id();
        CombatCardEffectContext context = new CombatCardEffectContext(
                state, line -> { }, upgraded::add, targetId, false);

        assertTrue(context.upgradeCard());
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

    private static CombatCardEffectContext newContext(
            BattleState state, boolean upgraded) {
        return new CombatCardEffectContext(
                state, line -> { }, card -> { }, null, upgraded);
    }
}
