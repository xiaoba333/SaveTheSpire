package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.entity.StatusEffect;

/**
 * 熔岩领主（Boss）：蓄能—爆发两阶段，且爆发间隔逐轮缩短。
 *
 * <p>循环：先连续蓄能若干回合（每次护甲 +12、力量 +2），然后打出「熔爆」（20 + 力量层数）；
 * 每完成一次熔爆，下一轮的蓄能回合数 −1（3 → 2 → 1，最少 1 回合）。</p>
 *
 * <p>设计意图：给玩家一个清楚可读的时间窗口。第一轮有 3 回合可以安心输出，
 * 之后窗口越来越窄，压力自然升级，不需要靠堆数值来制造难度。</p>
 */
public final class MagmaLordAi implements MonsterAi {

    private static final int CHARGE_ROUNDS = 3;
    private static final int CHARGE_BLOCK = 12;
    private static final int CHARGE_STRENGTH = 2;
    private static final int ERUPTION_BASE = 20;

    /** 本轮的蓄能回合数上限，每爆发一次减 1。 */
    private int chargeLength = CHARGE_ROUNDS;
    /** 本轮还剩几个蓄能回合。 */
    private int remainingCharge = CHARGE_ROUNDS;

    @Override
    public String name() {
        return "熔岩领主";
    }

    @Override
    public int maxHp() {
        return 120;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        if (remainingCharge > 0) {
            return "下回合：蓄能（护甲 +" + CHARGE_BLOCK + "，力量 +"
                    + CHARGE_STRENGTH + "，剩 " + remainingCharge + " 回合）";
        }
        return "下回合：熔爆 " + state.monsterAttackDamage(ERUPTION_BASE);
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        if (remainingCharge > 0) {
            return new IntentSnapshot("BUFF", CHARGE_STRENGTH);
        }
        return new IntentSnapshot("ATTACK", state.monsterAttackDamage(ERUPTION_BASE));
    }

    @Override
    public void startFight() {
        chargeLength = CHARGE_ROUNDS;
        remainingCharge = CHARGE_ROUNDS;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);

        if (remainingCharge > 0) {
            remainingCharge--;
            state.addMonsterBlock(CHARGE_BLOCK);
            state.addMonsterStatus(StatusEffect.STRENGTH, CHARGE_STRENGTH);
            return MonsterTurnResult.buff(CHARGE_STRENGTH,
                    "蓄能：护甲 +" + CHARGE_BLOCK + "，力量 +" + CHARGE_STRENGTH
                            + "（还差 " + remainingCharge + " 回合爆发）。");
        }

        int dealt = state.monsterAttackPlayer(ERUPTION_BASE);
        chargeLength = Math.max(1, chargeLength - 1);
        remainingCharge = chargeLength;
        return MonsterTurnResult.attack(dealt);
    }
}
