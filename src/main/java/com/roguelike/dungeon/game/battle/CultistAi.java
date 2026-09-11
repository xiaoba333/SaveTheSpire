package com.roguelike.dungeon.game.battle;

/**
 * 邪教徒：每回合攻击，且攻击力逐回合增长（仪式）。
 */
public final class CultistAi implements MonsterAi {

    private static final int START_ATTACK = 6;
    private static final int RITUAL_GAIN = 3;

    private int attackDamage = START_ATTACK;

    @Override
    public String name() {
        return "邪教徒";
    }

    @Override
    public int maxHp() {
        return 48;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return "下回合：攻击 " + attackDamage;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return new IntentSnapshot("ATTACK", attackDamage);
    }

    @Override
    public void startFight() {
        attackDamage = START_ATTACK;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        int dealt = state.monsterAttackPlayer(attackDamage);
        attackDamage += RITUAL_GAIN;
        return MonsterTurnResult.attack(dealt);
    }
}
