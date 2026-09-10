package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardPlayServiceTest {

    @Test
    void playStrikeShouldConsumeEnergyAndMoveToDiscard() {
        BattleState state = readyState(3);
        CardPlayService service = new CardPlayService(line -> { }, card -> { });

        assertEquals(PlayCardResult.SUCCESS, service.play(state, "s1"));
        assertEquals(2, state.getPlayer().getEnergy());
        assertEquals(1, state.getPiles().getHandSize());
        assertEquals(1, state.getPiles().getDiscardPileSize());
        assertEquals(24, state.getMonsterHp());
    }

    @Test
    void playShouldFailWhenEnergyNotEnough() {
        BattleState state = readyState(0);
        CardPlayService service = new CardPlayService(line -> { }, card -> { });

        assertEquals(PlayCardResult.NOT_ENOUGH_ENERGY, service.play(state, 0));
        assertEquals(2, state.getPiles().getHandSize());
        assertEquals(0, state.getPiles().getDiscardPileSize());
        assertEquals(30, state.getMonsterHp());
    }

    @Test
    void playShouldFailWhenNotPlayerTurn() {
        BattleState state = readyState(3);
        state.setPlayerTurn(false);
        CardPlayService service = new CardPlayService(line -> { }, card -> { });

        assertEquals(PlayCardResult.NOT_PLAYER_TURN, service.play(state, 0));
    }

    @Test
    void playByInstanceIdShouldFindCard() {
        BattleState state = readyState(3);
        String id = state.getPiles().getHand().getFirst().id();
        CardPlayService service = new CardPlayService(line -> { }, card -> { });

        assertEquals(PlayCardResult.SUCCESS, service.play(state, id));
        assertEquals(1, state.getPiles().getDiscardPileSize());
    }

    private static BattleState readyState(int energy) {
        Player player = new Player(50, 3);
        player.setEnergy(energy);
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
}
