package com.roguelike.dungeon.game.entity;

/**
 * 遗物触发点。
 *
 * <p>遗物通过 {@link Relic#triggers()} 声明自己关心哪些时机，
 * 战斗流程在对应位置调用分发器，遗物本身不需要关心流程细节。</p>
 *
 * <p>数值约定：{@link #DAMAGE_DEALT} 与 {@link #DAMAGE_TAKEN} 的数值为
 * <b>易伤 / 虚弱结算之前的原始伤害</b>，遗物可以在此基础上加减成。</p>
 */
public enum RelicTrigger {

    /** 获得遗物时触发一次。 */
    OBTAIN("获得遗物"),

    /** 每场战斗开始，玩家第一回合的护甲清空之后触发。 */
    BATTLE_START("战斗开始"),

    /** 玩家回合开始，在清空护甲、刷新能量之后触发。 */
    TURN_START("回合开始"),

    /** 玩家回合结束，在弃掉手牌之前触发（此时仍可读到本回合护甲与剩余能量）。 */
    TURN_END("回合结束"),

    /** 成功打出一张牌之后触发。 */
    CARD_PLAYED("打出卡牌"),

    /** 玩家对怪物造成伤害之前触发，数值为原始伤害。 */
    DAMAGE_DEALT("造成伤害"),

    /** 玩家受到伤害之前触发，数值为易伤结算前的原始伤害。 */
    DAMAGE_TAKEN("受到伤害"),

    /** 怪物生命归零时触发。 */
    ENEMY_KILLED("击杀敌人"),

    /** 战斗胜利时触发。失败会直接结束整局，因此不触发。 */
    BATTLE_END("战斗胜利");

    private final String displayName;

    RelicTrigger(String displayName) {
        this.displayName = displayName;
    }

    /** 中文名称，例如「回合开始」。 */
    public String displayName() {
        return displayName;
    }
}
