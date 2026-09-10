package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;

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
    private final String targetCardId;
    private final boolean upgraded;

    /**
     * @param state 当前战斗状态
     * @param logger 已有战斗日志输出（不新增回放能力）
     * @param cardUpgradeHandler 手牌升级后同步永久牌组
     * @param targetCardId 锻造牌要升级的目标手牌实例 id；非锻造牌为 null
     * @param upgraded 当前打出的牌实例是否已经升级
     */
    public CombatCardEffectContext(
            BattleState state,
            Consumer<String> logger,
            Consumer<CardInstance> cardUpgradeHandler,
            String targetCardId,
            boolean upgraded) {
        this.state = Objects.requireNonNull(state, "战斗状态不能为 null");
        this.player = state.getPlayer();
        this.piles = state.getPiles();
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.cardUpgradeHandler = Objects.requireNonNull(
                cardUpgradeHandler, "卡牌升级处理器不能为 null");
        this.targetCardId = targetCardId;
        this.upgraded = upgraded;
    }

    @Override
    public boolean dealDamageToMonster(int amount) {
        int dealt = state.applyDamage(true, normalizeAmount(amount));
        log("对怪物造成 " + dealt + " 点伤害。");
        return state.getMonsterHp() <= 0;
    }

    @Override
    public void addMonsterBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        state.addMonsterBlock(amount);
        log("怪物获得 " + amount + " 点护甲。");
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
        player.addArmor(amount);
        log("玩家获得 " + amount + " 点护甲。");
    }

    @Override
    public void healPlayer(int amount) {
        if (amount <= 0) {
            return;
        }
        int before = player.getHealth();
        player.heal(amount);
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
    public boolean isUpgraded() {
        return upgraded;
    }

    @Override
    public int getPlayerHealth() {
        return player.getHealth();
    }

    @Override
    public void increasePlayerMaxHealth(int amount) {
        player.increaseMaxHealth(amount);
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
    public void log(String line) {
        logger.accept(line);
    }

    /** 把负数伤害修正为 0，避免无效负数进入伤害结算。 */
    private int normalizeAmount(int amount) {
        return Math.max(0, amount);
    }

}
