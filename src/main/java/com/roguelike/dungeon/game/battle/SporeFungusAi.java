package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.entity.StatusEffect;

/**
 * 孢子真菌：按「孢子云 → 硬化 → 啃咬」三步循环。
 *
 * <p>定位是「惩罚拖回合」：孢子云不断给玩家叠中毒，逼玩家赶在毒伤累积压垮自己之前结束战斗；
 * 中间夹一次叠甲，抵消玩家一回合的输出节奏。</p>
 */
public final class SporeFungusAi implements MonsterAi {

    private static final int SPORE_POISON = 3;
    private static final int HARDEN_BLOCK = 6;
    private static final int BITE = 9;
    private static final int CYCLE = 3;

    private int step = 0;

    @Override
    public String name() {
        return "孢子真菌";
    }

    @Override
    public int maxHp() {
        return 34;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return switch (step % CYCLE) {
            case 0 -> "下回合：孢子云（玩家 +" + SPORE_POISON + " 层中毒）";
            case 1 -> "下回合：防御 +" + HARDEN_BLOCK;
            default -> "下回合：攻击 " + BITE;
        };
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return switch (step % CYCLE) {
            case 0 -> new IntentSnapshot("DEBUFF", SPORE_POISON);
            case 1 -> new IntentSnapshot("DEFEND", HARDEN_BLOCK);
            default -> new IntentSnapshot("ATTACK", BITE);
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
                state.addPlayerStatus(StatusEffect.POISON, SPORE_POISON);
                yield MonsterTurnResult.debuff(SPORE_POISON,
                        "孢子云：玩家获得 " + SPORE_POISON + " 层中毒。");
            }
            case 1 -> {
                state.addMonsterBlock(HARDEN_BLOCK);
                yield MonsterTurnResult.defend(HARDEN_BLOCK);
            }
            default -> MonsterTurnResult.attack(state.monsterAttackPlayer(BITE));
        };
        step++;
        return result;
    }
}
