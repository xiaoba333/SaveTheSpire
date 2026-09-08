package com.roguelike.dungeon.game.card;

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

    /** 向战斗日志追加一行文本。 */
    void log(String line);
}
