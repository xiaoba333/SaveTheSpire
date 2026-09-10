package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Power;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 卡牌效果与战斗状态之间的适配器。
 *
 * <p>只依赖 {@link BattleState} 与回调，不引用 {@link Combat}。
 * 卡牌效果只调用 {@link CardEffectContext} 方法。</p>
 */
public final class CombatCardEffectContext implements CardEffectContext {

    private final BattleState state;
    private final Player player;
    private final CardPiles piles;
    private final Consumer<String> logger;
    private final Consumer<CardInstance> cardUpgradeHandler;
    private final double effectMultiplier;
    private final String targetCardId;

    /**
     * @param state 当前战斗状态
     * @param logger 已有战斗日志输出（不新增回放能力）
     * @param cardUpgradeHandler 手牌升级后同步永久牌组
     * @param effectMultiplier 卡牌效果倍率，普通牌为 1.0，升级牌为 1.25
     * @param targetCardId 锻造牌要升级的目标手牌实例 id；非锻造牌为 null
     */
    public CombatCardEffectContext(
            BattleState state,
            Consumer<String> logger,
            Consumer<CardInstance> cardUpgradeHandler,
            double effectMultiplier,
            String targetCardId) {
        this.state = Objects.requireNonNull(state, "战斗状态不能为 null");
        this.player = state.getPlayer();
        this.piles = state.getPiles();
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.cardUpgradeHandler = Objects.requireNonNull(
                cardUpgradeHandler, "卡牌升级处理器不能为 null");
        this.effectMultiplier = effectMultiplier;
        this.targetCardId = targetCardId;
    }

    @Override
    public void dealDamageToMonster(int amount) {
        int scaled = scaleAmount(amount);
        int dealt = state.applyDamage(true, scaled);
        log("对怪物造成 " + dealt + " 点伤害。");
    }

    @Override
    public void addMonsterBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        int scaled = scaleAmount(amount);
        state.addMonsterBlock(scaled);
        log("怪物获得 " + scaled + " 点护甲。");
    }

    @Override
    public void dealDamageToPlayer(int amount) {
        // 自伤类卡牌不参与默认倍率，避免升级后反而更亏。
        int dealt = state.applyDamage(false, normalizeAmount(amount));
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
    public boolean upgradeCard() {
        if (targetCardId == null || targetCardId.isBlank()) {
            log("未选择锻造目标。");
            return false;
        }
        CardInstance upgraded = piles.upgradeInHand(targetCardId);
        if (upgraded == null) {
            log("无法升级目标手牌。");
            return false;
        }
        cardUpgradeHandler.accept(upgraded);
        log("「" + upgraded.card().name() + "」已升级。");
        return true;
    }

    @Override
    public void gainPower(Power power) {
        player.gainPower(power);
        log("获得能力「" + power.name() + "」。");
    }

    @Override
    public void gainMaxHealth(int amount) {
        if (amount <= 0) {
            return;
        }
        player.increaseMaxHealth(amount);
        log("最大生命提升 " + amount + " 点。");
    }

    @Override
    public int getPlayerHealth() {
        return player.getHealth();
    }

    @Override
    public int getMonsterHealth() {
        return state.getMonsterHp();
    }

    @Override
    public void losePlayerHp(int amount) {
        int lost = player.takeDamage(normalizeAmount(amount));
        log("玩家失去 " + lost + " 点生命。");
    }

    @Override
    public void log(String line) {
        logger.accept(line);
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
