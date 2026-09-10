package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;

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

    public CardPlayService(
            Consumer<String> logger,
            Consumer<CardInstance> cardUpgradeHandler) {
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.cardUpgradeHandler = Objects.requireNonNull(
                cardUpgradeHandler, "卡牌升级处理器不能为 null");
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
            logger.accept("「" + card.name() + "」无法打出。");
            return PlayCardResult.CARD_NOT_PLAYABLE;
        }

        int actualCost = instance.effectiveCost();
        if (!tryConsumeEnergy(player, actualCost)) {
            logger.accept("能量不足，无法打出「" + card.name() + "」。");
            return PlayCardResult.NOT_ENOUGH_ENERGY;
        }

        piles.removeFromHand(handIndex);
        logger.accept("玩家打出「" + card.name() + "」，消耗 " + actualCost + " 点能量。");
        card.effect().apply(new CombatCardEffectContext(
                state,
                logger,
                cardUpgradeHandler,
                targetCardId,
                instance.upgraded()));

        if (card.exhausts()) {
            piles.sendToExhaust(instance);
            logger.accept("「" + card.name() + "」已消耗。");
        } else {
            piles.sendToDiscard(instance);
        }
        return PlayCardResult.SUCCESS;
    }

    private static boolean tryConsumeEnergy(Player player, int cost) {
        if (cost < 0) {
            return false;
        }
        return player.consume(cost);
    }
}
