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
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return switch (step % 3) {
            case 0 -> "下回合：防御 +" + CURL_BLOCK;
            case 1 -> "下回合：攻击 " + BITE;
            default -> "下回合：攻击 " + HEAVY_BITE;
        };
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return switch (step % 3) {
            case 0 -> new IntentSnapshot("DEFEND", CURL_BLOCK);
            case 1 -> new IntentSnapshot("ATTACK", BITE);
            default -> new IntentSnapshot("ATTACK", HEAVY_BITE);
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
        MonsterTurnResult result = switch (step % 3) {
            case 0 -> {
                state.addMonsterBlock(CURL_BLOCK);
                yield MonsterTurnResult.defend(CURL_BLOCK);
            }
            case 1 -> MonsterTurnResult.attack(state.applyDamage(false, BITE));
            default -> MonsterTurnResult.attack(state.applyDamage(false, HEAVY_BITE));
        };
        step++;
        return result;
    }
}
