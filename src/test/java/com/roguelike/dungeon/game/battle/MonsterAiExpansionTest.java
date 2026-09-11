package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * 新增怪物 AI 与相关基础设施（怪物力量 / 虚弱、玩家减益）的行为测试。
 */
class MonsterAiExpansionTest {

    private static BattleState newState(MonsterAi monsterAi) {
        return new BattleState(
                new Player(200, 3),
                List.of(new CardInstance("s1", CardLibrary.STRIKE)),
                new CardPiles(line -> { }),
                monsterAi.maxHp());
    }

    @Test
    void sporeFungusPoisonsThenHardensThenBites() {
        MonsterAi ai = new SporeFungusAi();
        BattleState state = newState(ai);

        assertEquals("孢子真菌", ai.name());
        assertEquals("DEBUFF", ai.intentInfo(state).type());

        MonsterAi.MonsterTurnResult first = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.DEBUFF, first.kind());
        assertEquals(3, state.playerStacks(StatusEffect.POISON));

        MonsterAi.MonsterTurnResult second = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.DEFEND, second.kind());
        assertEquals(6, state.getMonsterBlock());

        ai.takeTurn(state);
        assertEquals(200 - 9, state.getPlayer().getHealth());
    }

    @Test
    void gargoyleBashesWithAccumulatedArmorAndHardensWhenBroken() {
        MonsterAi ai = new GargoyleAi();
        BattleState state = newState(ai);

        // 首回合身上没有护甲 → 只能硬化。
        MonsterAi.MonsterTurnResult first = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.DEFEND, first.kind());
        assertEquals(10, state.getMonsterBlock());

        // 玩家放着它不管，它带着 10 点护甲进入自己的回合 → 盾击，把护甲全换成伤害。
        assertEquals("ATTACK", ai.intentInfo(state).type());
        assertEquals(10, ai.intentInfo(state).value());
        MonsterAi.MonsterTurnResult bash = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.ATTACK, bash.kind());
        assertEquals(200 - 10, state.getPlayer().getHealth());
        assertEquals(0, state.getMonsterBlock());

        // 玩家在回合里把它的甲砍到 3（不足 8 点阈值）→ 它放弃盾击，改回硬化。
        state.setMonsterBlock(3);
        MonsterAi.MonsterTurnResult result = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.DEFEND, result.kind());
        // 尖塔规则：护甲在自己回合开始时失效，残余的 3 点不会累加，只重新硬化 +10。
        assertEquals(10, state.getMonsterBlock());
        assertEquals(200 - 10, state.getPlayer().getHealth());
    }

    @Test
    void toxicSlimeWeakensThenHitsTwice() {
        MonsterAi ai = new ToxicSlimeAi();
        BattleState state = newState(ai);

        ai.takeTurn(state);
        assertEquals(2, state.playerStacks(StatusEffect.WEAK));

        ai.takeTurn(state);
        assertEquals(200 - 7, state.getPlayer().getHealth());

        ai.takeTurn(state);
        assertEquals(200 - 7 - 11, state.getPlayer().getHealth());
    }

    @Test
    void bloodFrenzyRagesEveryThirdTurnAndScalesWithLostHp() {
        MonsterAi ai = new BloodFrenzyAi();
        BattleState state = newState(ai);
        assertEquals(70, ai.maxHp());

        ai.takeTurn(state);
        assertEquals(200 - 8, state.getPlayer().getHealth());

        ai.takeTurn(state);
        assertEquals(200 - 8 - 8, state.getPlayer().getHealth());

        // 第 3 次行动是「血怒」：力量 +2、护甲 +6，不打人。
        MonsterAi.MonsterTurnResult rage = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.BUFF, rage.kind());
        assertEquals(2, state.getMonsterStatusStacks(StatusEffect.STRENGTH));
        assertEquals(6, state.getMonsterBlock());
        assertEquals(200 - 16, state.getPlayer().getHealth());

        // 已损失 30 点生命 → 基础 8+3，再叠 2 点力量，共 13 点。
        state.setMonsterHp(state.getMonsterMaxHp() - 30);
        ai.takeTurn(state);
        assertEquals(200 - 16 - 13, state.getPlayer().getHealth());
    }

    @Test
    void twinHeadedHoundRendsHowlsThenPounces() {
        MonsterAi ai = new TwinHeadedHoundAi();
        BattleState state = newState(ai);
        assertEquals(64, ai.maxHp());

        MonsterAi.MonsterTurnResult rend = ai.takeTurn(state);
        assertEquals(MonsterAi.MonsterTurnResult.ActionKind.MULTI_ATTACK, rend.kind());
        assertEquals(12, rend.value());
        assertEquals(200 - 12, state.getPlayer().getHealth());

        ai.takeTurn(state);
        assertEquals(2, state.playerStacks(StatusEffect.VULNERABLE));

        ai.takeTurn(state);
        assertEquals(8, state.getMonsterBlock());

        // 猛扑 14 打在带易伤的玩家身上 → 21 点（易伤 +50%）。
        ai.takeTurn(state);
        assertEquals(200 - 12 - 21, state.getPlayer().getHealth());
    }

    @Test
    void magmaLordShortensChargeWindowAfterEachEruption() {
        MonsterAi ai = new MagmaLordAi();
        BattleState state = newState(ai);
        assertEquals(120, ai.maxHp());

        for (int i = 0; i < 3; i++) {
            MonsterAi.MonsterTurnResult charge = ai.takeTurn(state);
            assertEquals(MonsterAi.MonsterTurnResult.ActionKind.BUFF, charge.kind());
        }
        assertEquals(6, state.getMonsterStatusStacks(StatusEffect.STRENGTH));

        // 熔爆 20 + 力量 6 = 26
        ai.takeTurn(state);
        assertEquals(200 - 26, state.getPlayer().getHealth());

        // 第二轮蓄能回合数缩短为 2
        ai.takeTurn(state);
        ai.takeTurn(state);
        assertEquals(10, state.getMonsterStatusStacks(StatusEffect.STRENGTH));
        assertEquals("ATTACK", ai.intentInfo(state).type());

        ai.takeTurn(state);
        assertEquals(200 - 26 - 30, state.getPlayer().getHealth());
    }

    @Test
    void monsterWeakAndStrengthShouldAffectItsOwnAttack() {
        BattleState weakened = newState(MonsterAiService.regular());
        weakened.setMonsterWillAttack(true);
        weakened.addMonsterStatus(StatusEffect.WEAK, 2);
        MonsterAiService.regular().takeTurn(weakened);
        assertEquals(200 - 7, weakened.getPlayer().getHealth());

        BattleState strengthened = newState(MonsterAiService.regular());
        strengthened.setMonsterWillAttack(true);
        strengthened.addMonsterStatus(StatusEffect.STRENGTH, 4);
        MonsterAiService.regular().takeTurn(strengthened);
        assertEquals(200 - 14, strengthened.getPlayer().getHealth());
    }

    @Test
    void combatLogShouldDescribeBuffAndDebuffTurns() {
        Player player = new Player(200, 3);
        List<CardInstance> deck = IntStream.range(0, 10)
                .mapToObj(i -> new CardInstance("defend-" + i, CardLibrary.DEFEND))
                .toList();
        Combat combat = new Combat(
                player, deck, new ArrayList<String>()::add,
                result -> { }, card -> { }, new SporeFungusAi());

        combat.endPlayerTurn();

        assertTrue(combat.drainNewLogs().stream()
                        .anyMatch(line -> line.contains("孢子云")),
                "怪物减益行动应写入可读日志");
    }

    @Test
    void catalogShouldSeparateEasyEliteAndBossPools() {
        assertEquals(MagmaLordAi.class, MonsterCatalog.randomBoss(7L).getClass());

        Set<String> eliteNames = new HashSet<>();
        Set<String> easyNames = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            MonsterAi elite = MonsterCatalog.randomElite(seed);
            eliteNames.add(elite.name());
            // 同一颗种子必须选出同一只怪，保证重开一局结果可复现。
            assertEquals(elite.getClass(), MonsterCatalog.randomElite(seed).getClass());

            MonsterAi easy = MonsterCatalog.randomEasy(seed);
            easyNames.add(easy.name());
            assertEquals(easy.getClass(), MonsterCatalog.randomEasy(seed).getClass());
        }
        assertTrue(Set.of("血怒掠夺者", "双头猎犬").containsAll(eliteNames),
                "精英池不应混入普通怪：" + eliteNames);
        assertTrue(easyNames.size() > 1, "普通怪池应有多只怪可选：" + easyNames);
        assertTrue(eliteNames.stream().noneMatch(easyNames::contains),
                "精英怪不应出现在普通怪池里");
    }
}
