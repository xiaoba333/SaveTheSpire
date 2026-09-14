package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.intent.Intent;

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

    /**
     * 由怪物当前计划意图生成 UI 快照，给脚本怪 AI 用（{@link ScriptedMonsterAi}、
     * {@link MonsterEncounterAi} 以前各写一份、都把 value 硬编码成 0，于是前端那排意图数字一直不显示）。
     *
     * <p>数字取 {@link Intent#amount()}（意图自己声明的：攻击是伤害、防御是格挡量、削弱是层数）；
     * 攻击类再补上怪物当前的力量，因为实际打出来就是 {@code 基础伤害 + 力量}（见 Intents.attack）。
     * 0 表示没有数字可显示，前端据此不画。</p>
     *
     * <p>类型这里仍按老规矩塌缩：ATTACK_DEBUFF → "ATTACK"、DEFEND_BUFF → "DEFEND"，
     * 其余用枚举名。前端的图标表两种都认，塌缩是为了不改变现网行为。</p>
     *
     * @return 意图为空或怪物已死时返回 null（前端隐藏意图）
     */
    static IntentSnapshot snapshotOf(Monster monster) {
        if (monster == null || monster.isDead()) {
            return null;
        }
        Intent intent = monster.plannedIntent();
        if (intent == null) {
            return null;
        }
        int value = switch (intent.type()) {
            case ATTACK, ATTACK_DEBUFF -> intent.amount() + monster.getStrength();
            default -> intent.amount();
        };
        String type = switch (intent.type()) {
            case ATTACK, ATTACK_DEBUFF -> "ATTACK";
            case DEFEND, DEFEND_BUFF -> "DEFEND";
            default -> intent.type().name();
        };
        return new IntentSnapshot(type, value);
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
