package com.roguelike.dungeon.game.battle;

/**
 * 虱虫：按「蜷缩叠甲 → 撕咬」两步循环，血量少但节奏快。
 */
public final class LouseAi implements MonsterAi {

    private static final int CURL_BLOCK = 4;
    private static final int BITE = 7;

    private int step = 0;

    @Override
    public String name() {
        return "虱虫";
    }

    @Override
    public int maxHp() {
        return 13;
    }

    @Override
    public Combat.Intent nextIntent() {
        return step % 2 == 0
                ? new Combat.Intent("DEFEND", CURL_BLOCK)
                : new Combat.Intent("ATTACK", BITE);
    }

    @Override
    public void startFight() {
        step = 0;
    }

    @Override
    public void takeTurn(Combat combat, int turnNumber) {
        if (step % 2 == 0) {
            combat.addMonsterBlockInternal(CURL_BLOCK);
            combat.log("虱虫蜷缩，获得 " + CURL_BLOCK + " 点护盾。");
        } else {
            int dealt = combat.applyDamage(false, BITE);
            combat.log("虱虫撕咬，对玩家造成 " + dealt + " 点伤害。");
        }
        step++;
    }
}
