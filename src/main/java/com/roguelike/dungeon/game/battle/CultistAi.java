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
    public Combat.Intent nextIntent() {
        return new Combat.Intent("ATTACK", attackDamage);
    }

    @Override
    public void startFight() {
        attackDamage = START_ATTACK;
    }

    @Override
    public void takeTurn(Combat combat, int turnNumber) {
        int dealt = combat.applyDamage(false, attackDamage);
        combat.log("邪教徒攻击，对玩家造成 " + dealt + " 点伤害。");
        attackDamage += RITUAL_GAIN;
    }
}
