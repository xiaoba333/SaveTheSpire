package com.roguelike.dungeon.game.card;

/**
 * 卡牌效果可使用的战斗操作集合。
 *
 * <p>这个接口是卡牌层与实体层之间的边界。当前实现只暴露 MVP 所需操作，
 * 后续要加入易伤、虚弱、力量等状态时，可以在这里继续扩展。</p>
 *
 * <p>设计上，卡牌效果只依赖这个接口，不直接依赖 {@code Combat} 或未来的实体类。
 * 这样卡牌只关心“我要做什么”，不关心“怪物和玩家内部如何保存血量”。</p>
 */
public interface CardEffectContext {

    /**
     * 对怪物造成伤害，护甲先吸收。
     *
     * @param amount 原始伤害值，非正数会被调用方忽略或按规则处理
     */
    void dealDamageToMonster(int amount);

    /**
     * 给怪物增加护甲。
     *
     * @param amount 要增加的护甲值
     */
    void addMonsterBlock(int amount);

    /**
     * 对玩家造成伤害，护甲先吸收。
     *
     * @param amount 原始伤害值
     */
    void dealDamageToPlayer(int amount);

    /**
     * 给玩家增加护甲。
     *
     * @param amount 要增加的护甲值
     */
    void addPlayerBlock(int amount);

    /**
     * 为玩家恢复生命值。
     *
     * @param amount 期望恢复的生命值，最终不会超过玩家最大生命
     */
    void healPlayer(int amount);

    /**
     * 从抽牌堆抽若干张牌，抽不到时会把弃牌堆洗回。
     *
     * @param count 期望抽牌数量
     */
    void drawCards(int count);

    /**
     * 为玩家增加当前回合能量。
     *
     * @param amount 期望增加的能量，最终不会超过玩家最大能量
     */
    void addPlayerEnergy(int amount);

    /**
     * 向战斗日志追加一行文本。
     *
     * @param line 要追加的日志内容
     */
    void log(String line);
}
