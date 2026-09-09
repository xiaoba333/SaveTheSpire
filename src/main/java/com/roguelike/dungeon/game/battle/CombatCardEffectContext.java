package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;

import java.util.Objects;

/**
 * 卡牌效果与战斗状态之间的适配器。
 *
 * <p>这个类实现了 {@link CardEffectContext}。卡牌效果只调用上下文方法，
 * 不直接知道 {@link Combat}、玩家或敌人的内部字段。当前 MVP 中，
 * 玩家操作委托给 {@link Player}，怪物操作仍通过 Combat 的 package-private
 * 方法完成；后续 Combat 完全接入 Enemy 后，可以继续在这里收敛怪物操作。</p>
 */
final class CombatCardEffectContext implements CardEffectContext {

    private final Combat combat;
    private final Player player;
    private final CardPiles piles;
    private final double effectMultiplier;

    /**
     * @param combat 当前战斗对象，用于怪物伤害、怪物护甲和战斗日志
     * @param player 本局共享玩家对象
     * @param piles            本场战斗的牌堆管理器
     * @param effectMultiplier 卡牌效果倍率，普通牌为 1.0，升级牌为 1.25
     */
    CombatCardEffectContext(
            Combat combat,
            Player player,
            CardPiles piles,
            double effectMultiplier) {
        this.combat = Objects.requireNonNull(combat, "战斗对象不能为 null");
        this.player = Objects.requireNonNull(player, "玩家对象不能为 null");
        this.piles = Objects.requireNonNull(piles, "牌堆管理器不能为 null");
        this.effectMultiplier = effectMultiplier;
    }

    @Override
    public void dealDamageToMonster(int amount) {
        int scaled = scaleAmount(amount);
        int dealt = combat.applyDamage(true, scaled);
        log("对怪物造成 " + dealt + " 点伤害。");
    }

    @Override
    public void addMonsterBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        int scaled = scaleAmount(amount);
        combat.addMonsterBlockInternal(scaled);
        log("怪物获得 " + scaled + " 点护甲。");
    }

    @Override
    public void dealDamageToPlayer(int amount) {
        // 自伤类卡牌不参与默认倍率，避免升级后反而更亏。
        int dealt = combat.applyDamage(false, normalizeAmount(amount));
        log("玩家受到 " + dealt + " 点伤害。");
    }

    @Override
    public void addPlayerBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        int scaled = scaleAmount(amount);
        player.addArmor(scaled);
        log("玩家获得 " + scaled + " 点护甲。");
    }

    @Override
    public void healPlayer(int amount) {
        if (amount <= 0) {
            return;
        }
        int before = player.getHealth();
        int scaled = scaleAmount(amount);
        player.heal(scaled);
        log("玩家恢复 " + (player.getHealth() - before) + " 点生命。");
    }

    @Override
    public void drawCards(int count) {
        int drawn = piles.draw(count).size();
        log("额外抽 " + drawn + " 张牌。");
    }

    @Override
    public void addPlayerEnergy(int amount) {
        if (amount <= 0) {
            return;
        }
        int before = player.getEnergy();
        player.addEnergy(amount);
        log("玩家获得 " + (player.getEnergy() - before) + " 点能量。");
    }

    @Override
    public boolean upgradeCard(int handIndex) {
        CardInstance upgraded = piles.upgradeInHand(handIndex);
        if (upgraded == null) {
            log("无法升级目标手牌。");
            return false;
        }
        combat.notifyCardUpgraded(upgraded);
        log("「" + upgraded.card().name() + "」已升级。");
        return true;
    }

    @Override
    public void log(String line) {
        combat.log(line);
    }

    /** 把负数伤害修正为 0，避免无效负数进入伤害结算。 */
    private int normalizeAmount(int amount) {
        return Math.max(0, amount);
    }

    /**
     * 按当前牌实例的升级倍率缩放数值。
     *
     * @param amount 原始数值
     * @return 缩放后数值，四舍五入且不为负数
     */
    private int scaleAmount(int amount) {
        if (amount <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(amount * effectMultiplier));
    }
}
