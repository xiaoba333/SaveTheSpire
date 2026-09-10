package com.roguelike.dungeon.game.battle;

/**
 * 怪物回合 AI：攻防交替。
 *
 * <p>不依赖 {@link Combat}。普通怪与 Boss 共用同一套决策，仅攻击/叠甲数值可配置，
 * 默认都是 10 / 10，与重构前行为一致。</p>
 */
public final class MonsterAiService {

    public static final int DEFAULT_ATTACK = 10;
    public static final int DEFAULT_BLOCK = 10;

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

    /**
     * 执行一回合怪物行动：先清空护甲，再攻击或叠甲，并翻转下一动意图。
     */
    public MonsterTurnResult executeTurn(BattleState state) {
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

    /** 界面用意图文案，战斗已结束时返回「已倒下」。 */
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        return state.isMonsterWillAttack()
                ? "下回合：攻击 " + attackDamage
                : "下回合：防御 +" + blockAmount;
    }

    /**
     * 结构化意图。战斗已结束时返回 null，与原 {@code Combat.getMonsterIntentInfo} 一致。
     */
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return state.isMonsterWillAttack()
                ? new IntentSnapshot("ATTACK", attackDamage)
                : new IntentSnapshot("DEFEND", blockAmount);
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public int getBlockAmount() {
        return blockAmount;
    }

    /** 怪物意图快照，避免服务依赖 {@code Combat.Intent}。 */
    public record IntentSnapshot(String type, int value) {
    }

    /** 一回合怪物行动结果，供调度器写原有日志文案。 */
    public record MonsterTurnResult(boolean attacked, int value) {

        static MonsterTurnResult attack(int dealt) {
            return new MonsterTurnResult(true, dealt);
        }

        static MonsterTurnResult defend(int block) {
            return new MonsterTurnResult(false, block);
        }
    }
}
