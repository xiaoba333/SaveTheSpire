package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;

import java.util.Set;

/**
 * 遗物「钙化壳」：回合结束时保留一半护甲。
 *
 * <p>战斗流程在玩家回合开始时会调用 {@code clearArmor()} 清空护甲，
 * 因此这里在 {@link RelicTrigger#TURN_END}（护甲清空之前）把护甲预存下来，
 * 等 {@link RelicTrigger#TURN_START}（护甲清空之后）再补回去，
 * 相当于让一部分护甲跨回合存活。</p>
 *
 * <p>本遗物带跨回合状态，必须每次创建新实例，不能做成单例。</p>
 */
public final class CalcifiedShellRelic implements Relic {

    private int carriedArmor;

    @Override
    public String id() {
        return "calcified_shell";
    }

    @Override
    public String name() {
        return "钙化壳";
    }

    @Override
    public String description() {
        return "回合结束时保留一半护甲，不被清空。";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.RARE;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.TURN_END, RelicTrigger.TURN_START);
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (trigger == RelicTrigger.TURN_END) {
            carriedArmor = ctx.player().getArmor() / 2;
            return;
        }
        if (carriedArmor > 0) {
            int retained = carriedArmor;
            carriedArmor = 0;
            ctx.player().addArmor(retained);
            ctx.log("「钙化壳」保留了 " + retained + " 点护甲。");
        }
    }
}
