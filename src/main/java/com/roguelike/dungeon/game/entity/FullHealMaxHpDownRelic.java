package com.roguelike.dungeon.game.entity;

import java.util.Set;

/**
 * 遗物「血之代价」：每场战斗胜利后回满生命，但最大生命值永久 -1。
 *
 * <p>本遗物无自身状态——削减量直接落在 {@link Player#reduceMaxHealth(int)}
 * 上，因此每次开局都应创建新实例，避免多次开局共用一个对象。</p>
 *
 * <p>只挂在 {@link RelicTrigger#BATTLE_END} 上，复用统一的遗物分发器；
 * 故意不再实现 {@link Relic#onEnterLevel(Player)}，否则一旦将来
 * {@link Player#onEnterLevel()} 被接进主流程，效果会被重复结算两次。</p>
 */
public final class FullHealMaxHpDownRelic implements Relic {

    /** 遗物编号，与 {@code RelicLibrary.BLOOD_PRICE} 保持一致。 */
    public static final String ID = "blood_price";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "血之代价";
    }

    @Override
    public String description() {
        return "每场战斗胜利后回满生命，但最大生命值永久 -1。";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.UNCOMMON;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.BATTLE_END);
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (trigger != RelicTrigger.BATTLE_END) {
            return;
        }
        Player player = ctx.player();
        player.reduceMaxHealth(1);   // 最大生命值 -1（下限 1 点）
        player.healToFull();         // 回满血到新的上限
        ctx.log("「血之代价」触发：最大生命值 -1（" + player.getMaxHealth()
                + "），并回满生命。");
    }
}
