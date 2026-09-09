package com.roguelike.dungeon.game.reward;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardServiceTest {
    private static final List<Card> REWARD_POOL = List.of(
            CardLibrary.STRIKE,
            CardLibrary.DEFEND,
            CardLibrary.BASH,
            CardLibrary.QUICK_SLASH,
            CardLibrary.HEAVY_STRIKE);

    @Test
    void sameSeedShouldGenerateSameRewardChoices() {
        BattleReward first = RewardService.generateReward(REWARD_POOL, 12345L, 20);
        BattleReward second = RewardService.generateReward(REWARD_POOL, 12345L, 20);

        assertEquals(first, second);
        assertEquals(RewardService.CARD_CHOICE_COUNT, first.cardChoices().size());
    }

    @Test
    void claimingCardShouldAddCardAndGoldToRunState() {
        RunState state = newRunState(10);
        BattleReward reward = new BattleReward(20, List.of(CardLibrary.BASH));
        RewardService service = new RewardService(state, reward, () -> "reward-card-1");

        LevelResult result = service.claimCard(CardLibrary.BASH.id());

        assertEquals(LevelResult.COMPLETED, result);
        assertEquals(30, state.getGold());
        assertEquals(List.of(
                new CardInstance("reward-card-1", CardLibrary.BASH)), state.getDeck());
        assertTrue(service.isResolved());
    }

    @Test
    void skippingCardShouldOnlyAddGold() {
        RunState state = newRunState(10);
        RewardService service = new RewardService(
                state,
                new BattleReward(20, List.of(CardLibrary.BASH)),
                () -> "unused-id");

        LevelResult result = service.skipCard();

        assertEquals(LevelResult.COMPLETED, result);
        assertEquals(30, state.getGold());
        assertTrue(state.getDeck().isEmpty());
        assertTrue(service.isResolved());
    }

    @Test
    void selectingCardOutsideRewardShouldNotChangeRunState() {
        RunState state = newRunState(10);
        RewardService service = new RewardService(
                state,
                new BattleReward(20, List.of(CardLibrary.BASH)),
                () -> "reward-card-1");

        assertThrows(IllegalArgumentException.class,
                () -> service.claimCard(CardLibrary.DEFEND.id()));
        assertEquals(10, state.getGold());
        assertTrue(state.getDeck().isEmpty());
        assertFalse(service.isResolved());
    }

    @Test
    void resolvedRewardShouldNotBeClaimedTwice() {
        RunState state = newRunState(10);
        RewardService service = new RewardService(
                state,
                new BattleReward(20, List.of(CardLibrary.BASH)),
                () -> "reward-card-1");

        service.claimCard(CardLibrary.BASH.id());

        assertThrows(IllegalStateException.class, service::skipCard);
        assertEquals(30, state.getGold());
        assertEquals(1, state.getDeck().size());
    }

    @Test
    void duplicateDefinitionsInPoolShouldProduceUniqueChoices() {
        BattleReward reward = RewardService.generateReward(
                List.of(CardLibrary.STRIKE, CardLibrary.STRIKE, CardLibrary.DEFEND),
                12345L,
                20);

        assertEquals(2, reward.cardChoices().size());
        assertEquals(2, reward.cardChoices().stream().map(Card::id).distinct().count());
    }

    private static RunState newRunState(int gold) {
        return new RunState(new Player(50, 3), List.of(), gold, 12345L, 1);
    }
}
