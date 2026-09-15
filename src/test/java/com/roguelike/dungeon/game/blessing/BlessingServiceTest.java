package com.roguelike.dungeon.game.blessing;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlessingServiceTest {

    @Test
    void sameSeedShouldOfferSameThreeDistinctOptions() {
        List<BlessingType> first = BlessingService.rollTypes(12345L);
        List<BlessingType> second = BlessingService.rollTypes(12345L);

        assertEquals(BlessingService.CHOICE_COUNT, first.size());
        assertEquals(first, second);
        assertEquals(3, first.stream().distinct().count());
    }

    @Test
    void maxHealthBlessingShouldIncreaseMaxAndCurrentHp() {
        RunState state = newRunState();
        BlessingService service = new BlessingService(
                state, List.of(BlessingType.MAX_HP, BlessingType.GOLD, BlessingType.REMOVE_CARD));

        BlessingActionResult result = service.choose(BlessingType.MAX_HP.id());

        assertTrue(result.succeeded());
        assertEquals(56, state.getPlayer().getMaxHealth());
        assertEquals(56, state.getPlayer().getHealth());
        assertTrue(service.isResolved());
    }

    @Test
    void goldBlessingShouldAddOneHundredGold() {
        RunState state = newRunState();
        BlessingService service = new BlessingService(
                state, List.of(BlessingType.GOLD, BlessingType.MAX_HP, BlessingType.UPGRADE_CARD));

        assertTrue(service.choose(BlessingType.GOLD.id()).succeeded());
        assertEquals(100, state.getGold());
        assertEquals(BlessingType.GOLD.id(), service.chosenOptionId());
        assertEquals("获得了 " + BlessingService.GOLD_BONUS + " 金币。",
                service.resultMessage());
    }

    @Test
    void damageGoldBlessingShouldDealDamageAndAddGold() {
        RunState state = newRunState();
        BlessingService service = new BlessingService(
                state,
                List.of(BlessingType.DAMAGE_GOLD, BlessingType.GOLD, BlessingType.MAX_HP));

        assertTrue(service.choose(BlessingType.DAMAGE_GOLD.id()).succeeded());
        assertEquals(40, state.getPlayer().getHealth());
        assertEquals(200, state.getGold());
    }

    @Test
    void removeCardBlessingShouldDeleteChosenDeckCard() {
        RunState state = newRunState();
        BlessingService service = new BlessingService(
                state,
                List.of(BlessingType.REMOVE_CARD, BlessingType.GOLD, BlessingType.MAX_HP));

        BlessingActionResult pending = service.choose(BlessingType.REMOVE_CARD.id());
        assertTrue(pending.needsCard());
        assertEquals(2, state.getDeck().size());

        BlessingActionResult result = service.chooseCard("strike-1");

        assertTrue(result.succeeded());
        assertEquals(1, state.getDeck().size());
        assertEquals("defend-1", state.getDeck().getFirst().id());
    }

    @Test
    void upgradeCardBlessingShouldUpgradeChosenDeckCard() {
        RunState state = newRunState();
        BlessingService service = new BlessingService(
                state,
                List.of(BlessingType.UPGRADE_CARD, BlessingType.GOLD, BlessingType.MAX_HP));

        assertTrue(service.choose(BlessingType.UPGRADE_CARD.id()).needsCard());
        assertTrue(service.chooseCard("strike-1").succeeded());
        assertTrue(state.getDeck().getFirst().upgraded());
    }

    private static RunState newRunState() {
        return new RunState(
                new Player(50, 3),
                List.of(
                        new CardInstance("strike-1", CardLibrary.STRIKE),
                        new CardInstance("defend-1", CardLibrary.DEFEND)),
                0,
                12345L,
                1);
    }
}
