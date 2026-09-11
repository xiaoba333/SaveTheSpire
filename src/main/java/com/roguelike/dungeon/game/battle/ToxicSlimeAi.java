package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.entity.StatusEffect;

/**
 * 毒沼史莱姆：按「腐蚀之触 → 酸液喷吐 → 重击」三步循环。
 *
 * <p>定位是「压制输出节奏」：先给玩家挂虚弱（伤害 ×75%），再用一次中攻试探，
 * 最后用重击收账。玩家要么顶着虚弱强攻，要么先花一回合苟住。</p>
 */
public final class ToxicSlimeAi implements MonsterAi {

    private static final int WEAK_STACKS = 2;
    private static final int SPIT = 7;
    private static final int HEAVY_SPIT = 11;
    private static final int CYCLE = 3;

    private int step = 0;

    @Override
    public String name() {
        return "毒沼史莱姆";
    }

    @Override
    public int maxHp() {
        return 32;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return switch (step % CYCLE) {
            case 0 -> "下回合：腐蚀之触（玩家 +" + WEAK_STACKS + " 层虚弱）";
            case 1 -> "下回合：攻击 " + SPIT;
            default -> "下回合：重击 " + HEAVY_SPIT;
        };
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return switch (step % CYCLE) {
            case 0 -> new IntentSnapshot("DEBUFF", WEAK_STACKS);
            case 1 -> new IntentSnapshot("ATTACK", SPIT);
            default -> new IntentSnapshot("ATTACK", HEAVY_SPIT);
        };
    }

    @Override
    public void startFight() {
        step = 0;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        MonsterTurnResult result = switch (step % CYCLE) {
            case 0 -> {
                state.addPlayerStatus(StatusEffect.WEAK, WEAK_STACKS);
                yield MonsterTurnResult.debuff(WEAK_STACKS,
                        "腐蚀之触：玩家获得 " + WEAK_STACKS + " 层虚弱。");
            }
            case 1 -> MonsterTurnResult.attack(state.monsterAttackPlayer(SPIT));
            default -> MonsterTurnResult.attack(state.monsterAttackPlayer(HEAVY_SPIT));
        };
        step++;
        return result;
    }
}
