package com.roguelike.dungeon.game.battle;

/**
 * 可配置的通用怪物：攻击与叠甲交替循环（保留 MVP 原有行为）。
 *
 * <p>按名字、血量、攻击、叠甲构造，可复现「先攻击、再叠甲」的简单节奏；
 * 也作为卡牌效果测试里的「木桩」使用（attack/block 传 0）。
 * 「下一动是否攻击」的状态存在内部。</p>
 */
public final class DefaultMonsterAi implements MonsterAi {

    public static final int DEFAULT_MAX_HP = 30;
    public static final int DEFAULT_ATTACK = 10;
    public static final int DEFAULT_BLOCK = 10;

    private final String displayName;
    private final int maxHp;
    private final int attack;
    private final int block;
    private boolean willAttack = true;

    public DefaultMonsterAi() {
        this("守卫者", DEFAULT_MAX_HP, DEFAULT_ATTACK, DEFAULT_BLOCK);
    }

    public DefaultMonsterAi(String displayName, int maxHp, int attack, int block) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.attack = attack;
        this.block = block;
    }

    @Override
    public String name() {
        return displayName;
    }

    @Override
    public int maxHp() {
        return maxHp;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return willAttack
                ? "下回合：攻击 " + attack
                : "下回合：防御 +" + block;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return willAttack
                ? new IntentSnapshot("ATTACK", attack)
                : new IntentSnapshot("DEFEND", block);
    }

    @Override
    public void startFight() {
        willAttack = true;
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);
        if (willAttack) {
            int dealt = state.applyDamage(false, attack);
            willAttack = false;
            return MonsterTurnResult.attack(dealt);
        }
        state.addMonsterBlock(block);
        willAttack = true;
        return MonsterTurnResult.defend(block);
    }
}
