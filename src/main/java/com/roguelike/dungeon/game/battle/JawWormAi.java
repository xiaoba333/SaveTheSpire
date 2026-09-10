package com.roguelike.dungeon.game.battle;

/**
 * 颚虫：按「叠甲 → 撕咬 → 重撕咬」三步循环。
 */
public final class JawWormAi implements MonsterAi {

    private static final int CURL_BLOCK = 6;
    private static final int BITE = 8;
    private static final int HEAVY_BITE = 12;

    private int step = 0;

    @Override
    public String name() {
        return "颚虫";
    }

    @Override
    public int maxHp() {
        return 44;
    }

    @Override
    public Combat.Intent nextIntent() {
        return switch (step % 3) {
            case 0 -> new Combat.Intent("DEFEND", CURL_BLOCK);
            case 1 -> new Combat.Intent("ATTACK", BITE);
            default -> new Combat.Intent("ATTACK", HEAVY_BITE);
        };
    }

    @Override
    public void startFight() {
        step = 0;
    }

    @Override
    public void takeTurn(Combat combat, int turnNumber) {
        switch (step % 3) {
            case 0 -> {
                combat.addMonsterBlockInternal(CURL_BLOCK);
                combat.log("颚虫蜷缩，获得 " + CURL_BLOCK + " 点护盾。");
            }
            case 1 -> {
                int dealt = combat.applyDamage(false, BITE);
                combat.log("颚虫撕咬，对玩家造成 " + dealt + " 点伤害。");
            }
            default -> {
                int dealt = combat.applyDamage(false, HEAVY_BITE);
                combat.log("颚虫重撕咬，对玩家造成 " + dealt + " 点伤害。");
            }
        }
        step++;
    }
}
