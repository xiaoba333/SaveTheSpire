package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.enemy.Monster;

/**
 * 怪物 AI：决定怪物每回合的意图与行动。
 *
 * <p>所有怪物（普通怪 / Boss / 特定怪）都实现本接口，只面向 {@link BattleState}
 * 操作，不依赖 {@link Combat}。{@link Combat} 在玩家结束回合时把「怪物回合」委托
 * 给 {@link #takeTurn(BattleState)}，不再把攻击 / 叠甲逻辑写死在 Combat 里。</p>
 *
 * <p>多怪编队由 {@link MonsterEncounterAi} 实现：一个实例驱动一整组怪。
 * 单怪实现不需要关心名册，默认方法已给出兼容行为。</p>
 */
public interface MonsterAi {

    /** 怪物中文名。多怪编队返回当前锁定目标的名字，方便界面跟着高亮走。 */
    String name();

    /** 怪物英文标识（前端按此选择敌人视觉，如 "cultist" / "jawWormAlt"）。 */
    String id();

    /** 怪物最大生命值。 */
    int maxHp();

    /** 界面用意图文案；战斗已结束时返回「已倒下」。 */
    String intentText(BattleState state);

    /** 结构化意图，供 HTTP 层序列化；战斗已结束时返回 null。 */
    IntentSnapshot intentInfo(BattleState state);

    /**
     * 指定某只怪物的结构化意图，供 HTTP 层逐个敌人渲染意图图标。
     *
     * <p>单怪实现默认直接复用 {@link #intentInfo(BattleState)}。</p>
     */
    default IntentSnapshot intentInfoFor(BattleState state, Monster monster) {
        return intentInfo(state);
    }

    /**
     * 战斗开始时重置 AI 内部状态。
     *
     * <p>带 {@link BattleState} 的重载会在开打时调用，脚本怪需要它绑定实体。
     * 旧的无参版本留给测试木桩。</p>
     */
    default void startFight() {
    }

    default void startFight(BattleState state) {
        startFight();
    }

    /**
     * 执行一回合怪物行动（攻击玩家 / 给自己叠甲 / 成长等），并翻转下一回合意图。
     *
     * <p>多怪编队在自己的实现里遍历全部存活怪逐个行动。</p>
     *
     * @param state 当前战斗状态，AI 通过它施加伤害 / 叠甲
     * @return 本回合行动结果，供调度器写日志
     */
    MonsterTurnResult takeTurn(BattleState state);

    /**
     * 当前形态血量归零时尝试进入下一阶段。
     *
     * @return {@code true} 表示已变形且战斗继续，不应判胜
     */
    default boolean onHpDepleted(BattleState state) {
        return false;
    }

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
