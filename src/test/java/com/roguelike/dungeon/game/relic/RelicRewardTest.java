package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.reward.RewardService;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证遗物已经接入战斗奖励：奖励里能出现遗物，领取后真实生效。
 */
class RelicRewardTest {
    private static final List<Card> POOL = List.of(CardLibrary.BASH, CardLibrary.IRON_WAVE);

    @Test
    void generateRewardShouldAttachRelicWhenServiceProvided() {
        RunState runState = newRunState();
        RelicService relicService = new RelicService(runState.getPlayer(), line -> { });

        BattleReward reward = RewardService.generateReward(
                POOL, 12345L, 20, relicService, RelicRarity.COMMON);

        assertTrue(reward.hasRelic(), "接入遗物分发器后奖励应包含遗物");
        assertEquals(RelicRarity.COMMON, reward.relic().rarity());
    }

    @Test
    void generateRewardWithoutRelicServiceShouldHaveNoRelic() {
        BattleReward reward = RewardService.generateReward(POOL, 12345L, 20);
        assertFalse(reward.hasRelic());
    }

    @Test
    void claimingRewardShouldGrantRelicToPlayer() {
        RunState runState = newRunState();
        RelicService relicService = new RelicService(runState.getPlayer(), line -> { });
        RewardService service = new RewardService(
                runState, POOL, 12345L, 20, relicService, RelicRarity.COMMON);

        Relic offered = service.getReward().relic();
        assertTrue(offered != null);

        service.skipCard();

        assertTrue(relicService.has(offered.id()), "领取奖励后玩家应正式获得遗物");
        assertEquals(1, relicService.relics().size());
    }

    @Test
    void eliteShouldBeAbleToDropRareRelic() {
        RunState runState = newRunState();
        RelicService relicService = new RelicService(runState.getPlayer(), line -> { });
        RelicRarity[] elitePool = {
                RelicRarity.COMMON, RelicRarity.UNCOMMON, RelicRarity.RARE};

        boolean sawRare = false;
        for (long seed = 0; seed < 200 && !sawRare; seed++) {
            BattleReward reward = RewardService.generateReward(
                    POOL, seed, 35, relicService, elitePool);
            if (reward.hasRelic() && reward.relic().rarity() == RelicRarity.RARE) {
                sawRare = true;
            }
        }
        assertTrue(sawRare, "精英怪掉落池里应能抽到稀有遗物");
    }

    @Test
    void relicRewardShouldNotOfferAlreadyOwnedRelic() {
        RunState runState = newRunState();
        RelicService relicService = new RelicService(runState.getPlayer(), line -> { });
        // 先拿走一个普通遗物
        Relic owned = RelicLibrary.randomReward(1L, runState.getPlayer(),
                RelicRarity.COMMON).orElseThrow();
        relicService.acquire(owned);

        for (long seed = 0; seed < 100; seed++) {
            BattleReward reward = RewardService.generateReward(
                    POOL, seed, 20, relicService, RelicRarity.COMMON);
            if (reward.hasRelic()) {
                assertFalse(reward.relic().id().equals(owned.id()),
                        "不应重复掉落已持有的遗物");
            }
        }
    }

    private static RunState newRunState() {
        return new RunState(
                new Player(50, 3),
                List.of(new com.roguelike.dungeon.game.card.CardInstance(
                        "strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                1);
    }
}
