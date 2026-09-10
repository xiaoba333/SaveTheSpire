package com.roguelike.dungeon.game.battle;

/**
 * 酸液史莱姆：每回合稳定攻击，无变化，靠血量堆。
 */
public final class AcidSlimeAi implements MonsterAi {

    private static final int ATTACK = 8;

    @Override
    public String name() {
        return "酸液史莱姆";
    }

    @Override
    public int maxHp() {
        return 30;
    }

    @Override
    public Combat.Intent nextIntent() {
        return new Combat.Intent("ATTACK", ATTACK);
    }

    @Override
    public void startFight() {
    }

    @Override
    public void takeTurn(Combat combat, int turnNumber) {
        int dealt = combat.applyDamage(false, ATTACK);
        combat.log("酸液史莱姆攻击，对玩家造成 " + dealt + " 点伤害。");
    }
}
