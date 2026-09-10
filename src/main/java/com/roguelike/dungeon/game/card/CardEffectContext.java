package com.roguelike.dungeon.game.card;

import com.roguelike.dungeon.game.entity.Power;

/**
 * 卡牌效果可使用的战斗操作集合。
 *
 * <p>这个接口是卡牌层与实体层之间的边界。当前实现只暴露 MVP 所需操作，
 * 后续要加入易伤、虚弱、力量等状态时，可以在这里继续扩展。</p>
 */
public interface CardEffectContext {

    /**
     * 对怪物造成伤害，护甲先吸收。
     *
     * @return 本次伤害是否击杀怪物
     */
    boolean dealDamageToMonster(int amount);

    /** 给怪物增加护甲。 */
    void addMonsterBlock(int amount);

    /** 对玩家造成伤害，护甲先吸收。 */
    void dealDamageToPlayer(int amount);

    /** 给玩家增加护甲。 */
    void addPlayerBlock(int amount);

    /** 为玩家恢复生命值。 */
    void healPlayer(int amount);

    /** 从抽牌堆抽若干张牌，抽不到时会把弃牌堆洗回。 */
    void drawCards(int count);

    /** 为玩家增加当前回合能量。 */
    void addPlayerEnergy(int amount);

    /** 当前卡牌实例是否已经升级。 */
    boolean isUpgraded();

    /** 玩家当前生命值。 */
    int getPlayerHealth();

    /** 提高玩家最大生命值，不恢复当前生命。 */
    void increasePlayerMaxHealth(int amount);

    /**
     * 升级当前卡牌效果指定的目标手牌。
     *
     * <p>目标牌由出牌流程在调用卡牌效果前注入到上下文，因此这里不需要参数。
     * 锻造牌使用该方法升级玩家选中的目标牌；非锻造牌默认不会调用。</p>
     *
     * @return 升级成功返回 true，否则返回 false
     */
    boolean upgradeCard();

    /** 获得一个能力（能力牌打出时挂到玩家身上，每回合开始触发）。 */
    void gainPower(Power power);

    /** 向战斗日志追加一行文本。 */
    void log(String line);
}
