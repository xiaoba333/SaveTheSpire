package com.roguelike.dungeon.game.battle;

/**
 * 怪物 AI：决定怪物每回合的意图与行动。
 *
 * <p>所有怪物（普通怪 / Boss / 特定怪）都实现本接口，只面向 {@link BattleState}
 * 操作，不依赖 {@link Combat}。{@link Combat} 在玩家结束回合时把「怪物回合」委托
 * 给 {@link #takeTurn(BattleState)}，不再把攻击 / 叠甲逻辑写死在 Combat 里。</p>
 */
public interface MonsterAi {

    /** 怪物中文名。 */
    String name();

    /** 怪物英文标识（前端按此选择敌人视觉，如 "cultist" / "jawWormAlt"）。 */
    String id();

    /** 怪物最大生命值。 */
    int maxHp();

    /** 界面用意图文案；战斗已结束时返回「已倒下」。 */
    String intentText(BattleState state);

    /** 结构化意图，供 HTTP 层序列化；战斗已结束时返回 null。 */
    IntentSnapshot intentInfo(BattleState state);

    /** 战斗开始时重置 AI 内部状态。 */
    void startFight();

    /**
     * 执行一回合怪物行动（攻击玩家 / 给自己叠甲 / 成长等），并翻转下一回合意图。
     *
     * @param state 当前战斗状态，AI 通过它施加伤害 / 叠甲
     * @return 本回合行动结果，供调度器写日志
     */
    MonsterTurnResult takeTurn(BattleState state);

    /** 怪物意图快照。 */
    record IntentSnapshot(String type, int value) {
    }

    /** 一回合怪物行动结果。 */
    record MonsterTurnResult(boolean attacked, int value) {

        static MonsterTurnResult attack(int dealt) {
            return new MonsterTurnResult(true, dealt);
        }

        static MonsterTurnResult defend(int block) {
            return new MonsterTurnResult(false, block);
        }
    }
}
