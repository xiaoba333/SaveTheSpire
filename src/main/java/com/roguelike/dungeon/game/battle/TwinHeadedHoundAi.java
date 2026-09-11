package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.entity.StatusEffect;

/**
 * 双头猎犬（精英）：按「撕裂 → 咆哮 → 硬化 → 猛扑」四步循环。
 *
 * <p>两个头各咬一次，所以撕裂是 6 × 2 的两段伤害——专门克制「一次性大护甲」的应对方式，
 * 也让按次结算的遗物（荆棘之甲之类）更难受。咆哮给玩家挂易伤后再硬化，最后用猛扑收尾。</p>
 */
public final class TwinHeadedHoundAi implements MonsterAi {

    private static final int REND_DAMAGE = 6;
    private static final int REND_HITS = 2;
    private static final int HOWL_VULNERABLE = 2;
    private static final int HARDEN_BLOCK = 8;
    private static final int POUNCE = 14;
    private static final int CYCLE = 4;

    private int step = 0;

    @Override
    public String name() {
        return "双头猎犬";
    }

    @Override
    public int maxHp() {
        return 64;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return switch (step % CYCLE) {
            case 0 -> "下回合：撕裂 " + REND_DAMAGE + " × " + REND_HITS;
            case 1 -> "下回合：咆哮（玩家 +" + HOWL_VULNERABLE + " 层易伤）";
            case 2 -> "下回合：防御 +" + HARDEN_BLOCK;
            default -> "下回合：猛扑 " + POUNCE;
        };
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return switch (step % CYCLE) {
            case 0 -> new IntentSnapshot("MULTI_ATTACK",
                    state.monsterAttackDamage(REND_DAMAGE) * REND_HITS);
            case 1 -> new IntentSnapshot("DEBUFF", HOWL_VULNERABLE);
            case 2 -> new IntentSnapshot("DEFEND", HARDEN_BLOCK);
            default -> new IntentSnapshot("ATTACK", state.monsterAttackDamage(POUNCE));
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
                int total = 0;
                for (int i = 0; i < REND_HITS; i++) {
                    total += state.monsterAttackPlayer(REND_DAMAGE);
                }
                yield MonsterTurnResult.multiAttack(total, REND_HITS);
            }
            case 1 -> {
                state.addPlayerStatus(StatusEffect.VULNERABLE, HOWL_VULNERABLE);
                yield MonsterTurnResult.debuff(HOWL_VULNERABLE,
                        "咆哮：玩家获得 " + HOWL_VULNERABLE + " 层易伤。");
            }
            case 2 -> {
                state.addMonsterBlock(HARDEN_BLOCK);
                yield MonsterTurnResult.defend(HARDEN_BLOCK);
            }
            default -> MonsterTurnResult.attack(state.monsterAttackPlayer(POUNCE));
        };
        step++;
        return result;
    }
}
