package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.battle.BattleState;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.entity.StatusEffect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelicServiceTest {

    @Test
    void acquireShouldRegisterRelicAndSkipDuplicates() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        Relic anchor = RelicLibrary.create(RelicLibrary.ANCHOR);

        assertTrue(service.acquire(anchor));
        assertFalse(service.acquire(RelicLibrary.create(RelicLibrary.ANCHOR)));
        assertEquals(1, service.relics().size());
        assertTrue(service.has(RelicLibrary.ANCHOR));
    }

    @Test
    void fireShouldDispatchToInterestedRelicsOnly() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        // 磨刀石：你造成的伤害 +1
        service.acquire(RelicLibrary.create(RelicLibrary.WHETSTONE));

        assertEquals(7, service.fire(RelicTrigger.DAMAGE_DEALT, 6));
        // 不关心的触发点原值返回
        assertEquals(6, service.fire(RelicTrigger.DAMAGE_TAKEN, 6));
    }

    @Test
    void modifiersShouldStackInAcquireOrder() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        service.acquire(RelicLibrary.create(RelicLibrary.WHETSTONE));    // +1
        service.acquire(RelicLibrary.create(RelicLibrary.GLASS_CANNON)); // ×1.5

        // (6 + 1) × 1.5 = 10.5 → 11（四舍五入）
        assertEquals(11, service.fire(RelicTrigger.DAMAGE_DEALT, 6));
    }

    @Test
    void recursionShouldBeCappedByMaxDepth() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        // 一个永远在受伤时反弹伤害的遗物，用于构造递归
        service.acquire(new SimpleRelic("loop", "自环", "测试用",
                RelicRarity.COMMON, Set.of(RelicTrigger.DAMAGE_TAKEN),
                (trigger, ctx) -> service.fire(RelicTrigger.DAMAGE_TAKEN, 1)));

        // 深度上限内不会抛异常，且能正常返回
        int result = service.fire(RelicTrigger.DAMAGE_TAKEN, 5);
        assertTrue(result >= 0);
    }

    @Test
    void modifyCostShouldLetRelicChangeCost() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        service.acquire(new ZeroCostRelic());
        CardInstance strike = new CardInstance("s1", CardLibrary.STRIKE);

        assertEquals(1, strike.effectiveCost());
        assertEquals(0, service.modifyCost(strike));
        // 不持有遗物时费用不变
        assertEquals(1, new RelicService(new Player(50, 3), line -> { })
                .modifyCost(strike));
    }

    /** 测试用遗物：把所有牌费用改成 0。 */
    private static final class ZeroCostRelic implements Relic {
        @Override
        public String id() {
            return "zero_cost";
        }

        @Override
        public String name() {
            return "免费";
        }

        @Override
        public String description() {
            return "所有牌费用为 0。";
        }

        @Override
        public RelicRarity rarity() {
            return RelicRarity.RARE;
        }

        @Override
        public int modifyCost(CardInstance instance, int currentCost) {
            return 0;
        }
    }

    @Test
    void anchorShouldGrantArmorOnBattleStart() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        service.acquire(RelicLibrary.create(RelicLibrary.ANCHOR));
        BattleState state = newState(player);
        service.bindBattle(state);

        service.fire(RelicTrigger.BATTLE_START, 0);

        assertEquals(8, player.getArmor());
    }

    @Test
    void heartstoneShouldApplyVulnerableToMonster() {
        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        service.acquire(RelicLibrary.create(RelicLibrary.HEARTSTONE));
        BattleState state = newState(player);
        service.bindBattle(state);

        service.fire(RelicTrigger.TURN_START, 0);

        assertEquals(1, state.monsterStacks(StatusEffect.VULNERABLE));
    }

    @Test
    void bandageShouldHealAfterVictory() {
        Player player = new Player(50, 3);
        player.takeDamage(20);
        RelicService service = new RelicService(player, line -> { });
        service.acquire(RelicLibrary.create(RelicLibrary.BANDAGE));

        service.fire(RelicTrigger.BATTLE_END, 0);

        assertEquals(35, player.getHealth());
    }

    @Test
    void startingRelicsShouldAllBeAcquirable() {
        List<Relic> starting = RelicLibrary.createStarting();
        assertFalse(starting.isEmpty());

        Player player = new Player(50, 3);
        RelicService service = new RelicService(player, line -> { });
        for (Relic relic : starting) {
            assertTrue(service.acquire(relic), "初始遗物应可获得：" + relic.id());
        }
        assertEquals(starting.size(), service.relics().size());
    }

    @Test
    void libraryShouldCreateFreshInstancesEachTime() {
        Relic first = RelicLibrary.create(RelicLibrary.CALCIFIED_SHELL);
        Relic second = RelicLibrary.create(RelicLibrary.CALCIFIED_SHELL);
        assertFalse(first == second, "带状态遗物每次必须创建新实例");
    }

    @Test
    void randomRewardShouldSkipOwnedRelics() {
        Player player = new Player(50, 3);
        Relic first = RelicLibrary.randomReward(7L, player).orElseThrow();
        player.addRelic(first);

        List<Relic> drawn = new ArrayList<>();
        for (long seed = 0; seed < 50; seed++) {
            RelicLibrary.randomReward(seed, player, RelicRarity.COMMON)
                    .ifPresent(relic -> {
                        assertFalse(relic.id().equals(first.id()),
                                "不应抽到已持有的遗物");
                        drawn.add(relic);
                    });
        }
        assertFalse(drawn.isEmpty());
    }

    private static BattleState newState(Player player) {
        List<CardInstance> deck = List.of(new CardInstance("s1", CardLibrary.STRIKE));
        return new BattleState(player, deck, new CardPiles(line -> { }), 30);
    }
}
