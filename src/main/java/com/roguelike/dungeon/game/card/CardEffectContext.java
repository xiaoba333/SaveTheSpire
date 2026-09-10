package com.roguelike.dungeon.game.card;

import com.roguelike.dungeon.game.entity.Power;

/**
 * 卡牌效果可使用的战斗操作集合。
 *
 * <p>这个接口是卡牌层与实体层之间的边界。当前实现只暴露 MVP 所需操作，
 * 后续要加入易伤、虚弱、力量等状态时，可以在这里继续扩展。</p>
 */
public interface CardEffectContext {

    /** 对怪物造成伤害，护甲先吸收。 */
    void dealDamageToMonster(int amount);

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

    /**
     * 升级手牌中的一张牌。
     *
     * @param handIndex 要升级的手牌下标
     * @return 升级成功返回 true，否则返回 false
     */
    boolean upgradeCard(int handIndex);

    /** 获得一个能力（能力牌打出时挂到玩家身上，每回合开始触发）。 */
    void gainPower(Power power);

    /** 提升玩家最大生命值（当前生命不变）。 */
    void gainMaxHealth(int amount);

    /** 玩家当前生命值。 */
    int getPlayerHealth();

    /** 怪物当前生命值。 */
    int getMonsterHealth();

    /** 玩家直接失去生命值（绕过护甲和易伤，自伤类卡牌使用）。 */
    void losePlayerHp(int amount);

    /** 向战斗日志追加一行文本。 */
    void log(String line);
}
