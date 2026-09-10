package com.roguelike.dungeon.game.entity;

/**
 * 战斗内持续生效的能力（能力牌打出后挂到玩家身上的被动效果）。
 *
 * <p>与 {@link StatusEffect}（易伤 / 虚弱 / 中毒这类固定枚举）不同，能力是开放集合，
 * 每张能力牌对应一种能力，因此用接口表示，具体能力按需覆写触发钩子。</p>
 *
 * <p>能力属于战斗内临时状态：每场战斗开始时清空，玩家需要在每场战斗里重新打出
 * 能力牌来重新获得。能力牌本身仍保留在永久牌组中，下一场战斗可以再次打出。</p>
 */
public interface Power {

    /** 能力中文名。 */
    String name();

    /** 能力效果描述。 */
    String description();

    /**
     * 玩家回合开始时触发。默认空实现，具体能力按需覆写。
     *
     * @param player 本局唯一的玩家实体，能力直接在其上修改状态
     */
    default void onTurnStart(Player player) {
    }
}
