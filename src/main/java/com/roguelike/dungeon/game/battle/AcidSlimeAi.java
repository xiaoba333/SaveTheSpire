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
    public String id() {
        return "acidSlime";
    }

    @Override
    public int maxHp() {
        return 30;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return "下回合：攻击 " + ATTACK;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return new IntentSnapshot("ATTACK", ATTACK);
    }

    @Override
    public void startFight() {
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        int dealt = state.applyDamage(false, ATTACK);
        return MonsterTurnResult.attack(dealt);
    }
}
