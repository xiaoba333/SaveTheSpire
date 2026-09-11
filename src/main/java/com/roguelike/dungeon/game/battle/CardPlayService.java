package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.relic.RelicService;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 出牌流程：校验、扣费、执行效果、移牌。
 *
 * <p>不依赖 {@link Combat}，也不判定胜负。调度器在出牌成功后再检查结束。</p>
 */
public final class CardPlayService {

    private final Consumer<String> logger;
    private final Consumer<CardInstance> cardUpgradeHandler;
    /** 遗物分发器；为 null 时不做任何遗物结算，费用也用卡面原值。 */
    private final RelicService relicService;

    public CardPlayService(
            Consumer<String> logger,
            Consumer<CardInstance> cardUpgradeHandler) {
        this(logger, cardUpgradeHandler, null);
    }

    /**
     * 装配出牌流程，并接入遗物。
     *
     * @param relicService 本局共享的遗物分发器；传 null 表示本场不结算遗物
     */
    public CardPlayService(
            Consumer<String> logger,
            Consumer<CardInstance> cardUpgradeHandler,
            RelicService relicService) {
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.cardUpgradeHandler = Objects.requireNonNull(
                cardUpgradeHandler, "卡牌升级处理器不能为 null");
        this.relicService = relicService;
    }

    /** 按手牌下标出牌。 */
    public PlayCardResult play(BattleState state, int handIndex) {
        if (state.isFinished()) {
            return PlayCardResult.BATTLE_FINISHED;
        }
        if (!state.isPlayerTurn()) {
            return PlayCardResult.NOT_PLAYER_TURN;
        }
        if (handIndex < 0 || handIndex >= state.getPiles().getHandSize()) {
            return PlayCardResult.INVALID_CARD;
        }
        return playInternal(state, handIndex);
    }

    /** 按牌实例 id 出牌。 */
    public PlayCardResult play(BattleState state, String cardInstanceId) {
        return play(state, cardInstanceId, null);
    }

    /**
     * 按牌实例 id 出牌，并携带锻造目标牌实例 id。
     *
     * @param targetCardId 锻造牌要升级的目标手牌；非锻造牌可传 null
     */
    public PlayCardResult play(
            BattleState state,
            String cardInstanceId,
            String targetCardId) {
        if (state.isFinished()) {
            return PlayCardResult.BATTLE_FINISHED;
        }
        if (!state.isPlayerTurn()) {
            return PlayCardResult.NOT_PLAYER_TURN;
        }
        int handIndex = state.getPiles().findHandIndex(cardInstanceId);
        if (handIndex < 0) {
            return PlayCardResult.INVALID_CARD;
        }
        return playInternal(state, handIndex, targetCardId);
    }

    private PlayCardResult playInternal(BattleState state, int handIndex) {
        return playInternal(state, handIndex, null);
    }

    private PlayCardResult playInternal(
            BattleState state,
            int handIndex,
            String targetCardId) {
        CardPiles piles = state.getPiles();
        Player player = state.getPlayer();
        CardInstance instance = piles.peekHand(handIndex);
        Card card = instance.card();
        if (!card.playable()) {
            logger.accept("「" + instance.displayName() + "」无法打出。");
            return PlayCardResult.CARD_NOT_PLAYABLE;
        }

        // 费用先由遗物修正，再校验能量。X 费用牌直接消耗当前全部能量。
        int xCost = 0;
        int actualCost;
        if (card.id().equals(CardLibrary.BLOOD_RAIN.id())) {
            actualCost = player.getEnergy();
            if (actualCost <= 0) {
                logger.accept("能量不足，无法打出「" + instance.displayName() + "」。");
                return PlayCardResult.NOT_ENOUGH_ENERGY;
            }
            xCost = actualCost;
        } else {
            actualCost = relicService == null
                    ? instance.effectiveCost()
                    : relicService.modifyCost(instance);
        }
        if (!tryConsumeEnergy(player, actualCost)) {
            logger.accept("能量不足，无法打出「" + instance.displayName() + "」。");
            return PlayCardResult.NOT_ENOUGH_ENERGY;
        }

        piles.removeFromHand(handIndex);
        logger.accept("玩家打出「" + instance.displayName() + "」，消耗 " + actualCost + " 点能量。");
        card.effect().apply(new CombatCardEffectContext(
                state,
                logger,
                cardUpgradeHandler,
                targetCardId,
                instance.upgraded(),
                xCost));

        // 先累计出牌计数再分发遗物：苦无 / 手里剑需要看到「这是本场第几张攻击牌」。
        state.onCardPlayed(card.type());
        fireCardPlayed(instance);

        if (card.exhausts()) {
            piles.sendToExhaust(instance);
            logger.accept("「" + instance.displayName() + "」已消耗。");
        } else {
            piles.sendToDiscard(instance);
        }
        return PlayCardResult.SUCCESS;
    }

    /** 分发「打出卡牌」触发点；未接入遗物时忽略。 */
    private void fireCardPlayed(CardInstance instance) {
        if (relicService != null) {
            relicService.fire(RelicTrigger.CARD_PLAYED, 0, instance);
        }
    }

    private static boolean tryConsumeEnergy(Player player, int cost) {
        if (cost < 0) {
            return false;
        }
        return player.consume(cost);
    }
}
