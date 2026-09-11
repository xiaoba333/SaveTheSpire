package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;

import java.util.Set;

/**
 * 遗物「放血槽」：回合结束时每有 1 点未使用能量，下回合获得 2 点护甲。
 *
 * <p>护甲在回合结束阶段加上去会被下个回合开头的清空逻辑抹掉，
 * 所以这里先把护甲折算成「待发放」数值，留到 {@link RelicTrigger#TURN_START}
 * 再真正加上，才能起到防御作用。</p>
 *
 * <p>本遗物带跨回合状态，必须每次创建新实例。</p>
 */
public final class BloodlettingValveRelic implements Relic {

    /** 每点未使用能量折算的护甲量。 */
    public static final int ARMOR_PER_ENERGY = 2;

    private int pendingArmor;

    @Override
    public String id() {
        return "blood_letting_valve";
    }

    @Override
    public String name() {
        return "放血槽";
    }

    @Override
    public String description() {
        return "回合结束时每有 1 点未使用能量，下回合开始时获得 "
                + ARMOR_PER_ENERGY + " 点护甲。";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.UNCOMMON;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.TURN_END, RelicTrigger.TURN_START);
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (trigger == RelicTrigger.TURN_END) {
            pendingArmor = ctx.player().getEnergy() * ARMOR_PER_ENERGY;
            return;
        }
        if (pendingArmor > 0) {
            int armor = pendingArmor;
            pendingArmor = 0;
            ctx.player().addArmor(armor);
            ctx.log("「放血槽」转化出 " + armor + " 点护甲。");
        }
    }
}
