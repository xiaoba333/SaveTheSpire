package com.roguelike.dungeon.game.battle;

/**
 * 默认怪物 AI：攻击与叠甲交替循环（普通怪 / Boss 共用）。
 *
 * <p>不依赖 {@link Combat}。普通怪与 Boss 共用同一套决策，仅攻击/叠甲数值可配置，
 * 默认都是 10 / 10，与重构前行为一致。与 {@link DefaultMonsterAi} 不同，本实现的
 * 「下一动是否攻击」状态存于 {@link BattleState#isMonsterWillAttack()}。</p>
 */
public final class MonsterAiService implements MonsterAi {

    public static final int DEFAULT_ATTACK = 10;
    public static final int DEFAULT_BLOCK = 10;
    public static final int DEFAULT_MAX_HP = 30;
    public static final String DEFAULT_NAME = "守卫者";

    private final int attackDamage;
    private final int blockAmount;

    public MonsterAiService(int attackDamage, int blockAmount) {
        this.attackDamage = attackDamage;
        this.blockAmount = blockAmount;
    }

    /** 普通怪物：攻击 10，叠甲 10。 */
    public static MonsterAiService regular() {
        return new MonsterAiService(DEFAULT_ATTACK, DEFAULT_BLOCK);
    }

    /** Boss：当前数值与普通怪相同，便于后续单独调参。 */
    public static MonsterAiService boss() {
        return new MonsterAiService(DEFAULT_ATTACK, DEFAULT_BLOCK);
    }

    @Override
    public String name() {
        return DEFAULT_NAME;
    }

    @Override
    public int maxHp() {
        return DEFAULT_MAX_HP;
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return state.isMonsterWillAttack()
                ? "下回合：攻击 " + attackDamage
                : "下回合：防御 +" + blockAmount;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return state.isMonsterWillAttack()
                ? new IntentSnapshot("ATTACK", attackDamage)
                : new IntentSnapshot("DEFEND", blockAmount);
    }

    @Override
    public void startFight() {
        // 「下一动是否攻击」的状态存于 BattleState，由 Combat 开局时重置为攻击。
    }

    /**
     * 执行一回合怪物行动：先清空护甲，再攻击或叠甲，并翻转下一动意图。
     */
    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        state.setPlayerTurn(false);
        state.setMonsterBlock(0);

        if (state.isMonsterWillAttack()) {
            int dealt = state.applyDamage(false, attackDamage);
            state.setMonsterWillAttack(false);
            return MonsterTurnResult.attack(dealt);
        }
        state.addMonsterBlock(blockAmount);
        state.setMonsterWillAttack(true);
        return MonsterTurnResult.defend(blockAmount);
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public int getBlockAmount() {
        return blockAmount;
    }
}
