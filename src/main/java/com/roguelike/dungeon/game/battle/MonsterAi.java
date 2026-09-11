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

    /**
     * 一回合怪物行动结果。
     *
     * <p>{@link #value()} 的含义随 {@link ActionKind} 而定：
     * 攻击类为实际造成的伤害，防御为获得的护甲，增益 / 减益为层数或数值。</p>
     *
     * @param kind 行动类型
     * @param value 主要数值
     * @param text 行动描述，由 AI 自己组织，供 {@link Combat} 直接写日志
     */
    record MonsterTurnResult(ActionKind kind, int value, String text) {

        /** 行动类型。 */
        public enum ActionKind {

            /** 单次攻击。 */
            ATTACK("攻击"),

            /** 多段攻击。 */
            MULTI_ATTACK("多段攻击"),

            /** 给自己叠护甲。 */
            DEFEND("防御"),

            /** 给自身增益（力量 / 蓄能等）。 */
            BUFF("增益"),

            /** 给玩家施加减益（中毒 / 虚弱 / 易伤等）。 */
            DEBUFF("减益");

            private final String displayName;

            ActionKind(String displayName) {
                this.displayName = displayName;
            }

            /** 中文名称，例如「多段攻击」。 */
            public String displayName() {
                return displayName;
            }
        }

        public MonsterTurnResult {
            if (kind == null) {
                kind = ActionKind.ATTACK;
            }
            if (text == null) {
                text = "";
            }
        }

        /** 是否为攻击类行动（含多段攻击）。 */
        public boolean attacked() {
            return kind == ActionKind.ATTACK || kind == ActionKind.MULTI_ATTACK;
        }

        /** 是否为防御行动。 */
        public boolean defensive() {
            return kind == ActionKind.DEFEND;
        }

        static MonsterTurnResult attack(int dealt) {
            return new MonsterTurnResult(ActionKind.ATTACK, dealt,
                    "攻击，对玩家造成 " + dealt + " 点伤害。");
        }

        static MonsterTurnResult multiAttack(int totalDealt, int hits) {
            return new MonsterTurnResult(ActionKind.MULTI_ATTACK, totalDealt,
                    "连续攻击 " + hits + " 次，共造成 " + totalDealt + " 点伤害。");
        }

        static MonsterTurnResult defend(int block) {
            return new MonsterTurnResult(ActionKind.DEFEND, block,
                    "防御，获得 " + block + " 点护盾。");
        }

        /** 自身增益；{@code text} 描述具体效果。 */
        static MonsterTurnResult buff(int value, String text) {
            return new MonsterTurnResult(ActionKind.BUFF, value, text);
        }

        /** 给玩家施加减益；{@code text} 描述具体效果。 */
        static MonsterTurnResult debuff(int value, String text) {
            return new MonsterTurnResult(ActionKind.DEBUFF, value, text);
        }
    }
}
