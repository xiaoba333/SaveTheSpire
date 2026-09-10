package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.Set;

/**
 * 遗物「不死鸟之羽」：受到致死伤害时免于倒下，并回复 30% 生命。每局仅一次。
 *
 * <p>实现方式是 {@link RelicContext#cancel()}：伤害结算被取消，本次伤害按 0 处理。
 * 需要在判定前把易伤算进去，否则会低估致死伤害。</p>
 *
 * <p>本遗物带「是否已用掉」的状态，必须每次创建新实例。</p>
 */
public final class PhoenixFeatherRelic implements Relic {

    /** 复活时回复的最大生命百分比。 */
    public static final int HEAL_PERCENT = 30;

    private boolean consumed;

    @Override
    public String id() {
        return "phoenix_feather";
    }

    @Override
    public String name() {
        return "不死鸟之羽";
    }

    @Override
    public String description() {
        return "生命归零时免于倒下，改为回复 " + HEAL_PERCENT + "% 生命。每局仅一次。";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.RARE;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.DAMAGE_TAKEN);
    }

    /** 该遗物是否已经用掉了复活机会。 */
    public boolean isConsumed() {
        return consumed;
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (consumed) {
            return;
        }
        Player player = ctx.player();
        int incoming = ctx.value();
        if (player.hasStatus(StatusEffect.VULNERABLE)) {
            incoming = incoming * 3 / 2;   // 与 Player.receiveDamage 的易伤结算保持一致
        }
        if (player.getHealth() - incoming > 0) {
            return;
        }

        consumed = true;
        ctx.cancel();
        int heal = Math.max(1, player.getMaxHealth() * HEAL_PERCENT / 100);
        player.heal(heal);
        ctx.log("「不死鸟之羽」触发：免于倒下，回复 " + heal + " 点生命。");
    }
}
