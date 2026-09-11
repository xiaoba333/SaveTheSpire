package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.entity.StatusEffect;

/**
 * 血怒掠夺者（精英）：伤害随自身已损失生命成长，并周期性进入「血怒」。
 *
 * <p>攻击 = 8 + 已损失生命 / 10（再由 {@link BattleState#monsterAttackPlayer(int)}
 * 叠加力量层数）；每 3 次行动中有一次不攻击，而是「血怒」：力量 +2、护甲 +6。</p>
 *
 * <p>设计意图：玩家越拖、越是用大伤害换血，它的拳头就越重——逼玩家算清斩杀线，
 * 而不是无脑对拆。</p>
 */
public final class BloodFrenzyAi implements MonsterAi {

    private static final int BASE_ATTACK = 8;
    private static final int HP_LOST_PER_BONUS = 10;
    private static final int RAGE_INTERVAL = 3;
    private static final int RAGE_STRENGTH = 2;
    private static final int RAGE_BLOCK = 6;

    /** 本场已完成的行动次数，用于判断下一次是否为「血怒」。 */
    private int turn = 0;

    @Override
    public String name() {
        return "血怒掠夺者";
    }

    @Override
    public int maxHp() {
        return 70;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        if (isRageTurn()) {
            return "下回合：血怒（力量 +" + RAGE_STRENGTH + "，护甲 +" + RAGE_BLOCK + "）";
        }
        return "下回合：攻击 " + state.monsterAttackDamage(currentAttack(state));
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        if (isRageTurn()) {
            return new IntentSnapshot("BUFF", RAGE_STRENGTH);
        }
        return new IntentSnapshot("ATTACK", state.monsterAttackDamage(currentAttack(state)));
    }

    @Override
    public void startFight() {
        turn = 0;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        turn++;

        if (turn % RAGE_INTERVAL == 0) {
            state.addMonsterStatus(StatusEffect.STRENGTH, RAGE_STRENGTH);
            state.addMonsterBlock(RAGE_BLOCK);
            return MonsterTurnResult.buff(RAGE_STRENGTH,
                    "血怒：力量 +" + RAGE_STRENGTH + "，护甲 +" + RAGE_BLOCK + "。");
        }
        return MonsterTurnResult.attack(state.monsterAttackPlayer(currentAttack(state)));
    }

    /** 下一次行动是否为「血怒」。 */
    private boolean isRageTurn() {
        return (turn + 1) % RAGE_INTERVAL == 0;
    }

    /** 当前基础攻击力：基础值 + 每损失 10 点生命 +1。 */
    private int currentAttack(BattleState state) {
        return BASE_ATTACK + state.monsterHpLost() / HP_LOST_PER_BONUS;
    }
}
