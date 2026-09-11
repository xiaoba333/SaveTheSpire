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
    public String id() {
        return "louse";
    }

    @Override
    public int maxHp() {
        return 13;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return step % 2 == 0
                ? "下回合：防御 +" + CURL_BLOCK
                : "下回合：攻击 " + BITE;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return step % 2 == 0
                ? new IntentSnapshot("DEFEND", CURL_BLOCK)
                : new IntentSnapshot("ATTACK", BITE);
    }

    @Override
    public void startFight() {
        step = 0;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        if (step % 2 == 0) {
            state.addMonsterBlock(CURL_BLOCK);
            step++;
            return MonsterTurnResult.defend(CURL_BLOCK);
        }
        int dealt = state.applyDamage(false, BITE);
        step++;
        return MonsterTurnResult.attack(dealt);
    }
}
