package com.roguelike.dungeon.game.battle;

/**
 * 石像鬼：条件式 AI，按自身护甲决定行动。
 *
 * <p>护甲不足时「硬化 +10」；一旦带着 8 点以上护甲进入自己的回合，就转为「盾击」，
 * 用全部护甲打出等量伤害。它会读自己当前的护甲，所以玩家的选择直接影响它的行为：
 * 及时打掉它的甲，它就只会继续硬化；放着不管，就会被厚甲反击打穿。</p>
 */
public final class GargoyleAi implements MonsterAi {

    private static final int HARDEN_BLOCK = 10;
    private static final int ARMOR_THRESHOLD = 8;

    @Override
    public String name() {
        return "石像鬼";
    }

    @Override
    public int maxHp() {
        return 40;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        int armor = state.getMonsterBlock();
        return armor >= ARMOR_THRESHOLD
                ? "下回合：盾击 " + armor + "（消耗全部护甲）"
                : "下回合：硬化 +" + HARDEN_BLOCK;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        int armor = state.getMonsterBlock();
        return armor >= ARMOR_THRESHOLD
                ? new IntentSnapshot("ATTACK", armor)
                : new IntentSnapshot("DEFEND", HARDEN_BLOCK);
    }

    @Override
    public void startFight() {
        // 无内部状态：一切决策都基于当前护甲。
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        // 先读护甲再清空：这一轮的资源在上一个玩家回合结束时已经定型。
        int armor = state.getMonsterBlock();
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);

        if (armor >= ARMOR_THRESHOLD) {
            return MonsterTurnResult.attack(state.monsterAttackPlayer(armor));
        }
        state.addMonsterBlock(HARDEN_BLOCK);
        return MonsterTurnResult.defend(HARDEN_BLOCK);
    }
}
