package com.roguelike.dungeon.game.enemy.intent;

/**
 * 意图类型：决定 UI 上显示哪个图标，以及「这个意图大概有多大威胁」。
 *
 * <p>只做分类，不承载数值。数值都在 {@link Intent} 与 {@link Intents} 里。</p>
 */
public enum IntentType {

    /** 纯攻击：打 6、打 12、打 0*9。 */
    ATTACK("攻击"),

    /** 攻击 + 给玩家上负面状态：打 6 并给予一层易伤。 */
    ATTACK_DEBUFF("攻击并削弱"),

    /** 纯防御 / 叠甲：防 6、给同伴上 10 甲。 */
    DEFEND("防御"),

    /** 防御 + 强化自己：防 6 并获得 2 力量。 */
    DEFEND_BUFF("防御并强化"),

    /** 纯强化：两人都加一力量。 */
    BUFF("强化"),

    /** 纯削弱：给予玩家 99 层易伤。 */
    DEBUFF("削弱"),

    /** 回复：回 6、给两人回 6 血。 */
    HEAL("回复"),

    /** 特殊行为：破裂、召唤、向抽牌堆塞牌等无法归类的动作。 */
    SPECIAL("特殊"),

    /** 未知：无法预测时给玩家一个问号图标。 */
    UNKNOWN("未知");

    private final String chineseName;

    IntentType(String chineseName) {
        this.chineseName = chineseName;
    }

    /** 中文名，用于日志与界面提示。 */
    public String chineseName() {
        return chineseName;
    }
}
