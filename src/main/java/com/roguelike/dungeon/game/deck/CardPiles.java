package com.roguelike.dungeon.game.deck;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 管理一场战斗中的四类卡牌区域：抽牌堆、手牌、弃牌堆、消耗堆。
 */
public final class CardPiles {

    private final Random random;
    private final Consumer<String> logger;
    private final List<CardInstance> drawPile = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> discardPile = new ArrayList<>();
    private final List<CardInstance> exhaustPile = new ArrayList<>();

    public CardPiles(Consumer<String> logger) {
        this(logger, new Random());
    }

    public CardPiles(Consumer<String> logger, Random random) {
        this.logger = logger;
        this.random = random;
    }

    /** 清空所有区域，并装入一套已洗牌的初始牌组。 */
    public void initialize(List<Card> cards) {
        Objects.requireNonNull(cards, "牌组不能为 null");
        List<CardInstance> instances = new ArrayList<>(cards.size());
        for (Card card : cards) {
            instances.add(new CardInstance(
                    UUID.randomUUID().toString(),
                    Objects.requireNonNull(card, "牌组不能包含 null")));
        }
        initializeInstances(instances);
    }

    /**
     * 清空所有区域，并从一局游戏的永久牌组快照初始化战斗牌堆。
     * 只复制列表结构，不会改变 RunState 中的永久牌组顺序。
     */
    public void initializeInstances(List<CardInstance> cards) {
        Objects.requireNonNull(cards, "牌组不能为 null");
        drawPile.clear();
        hand.clear();
        discardPile.clear();
        exhaustPile.clear();
        for (CardInstance card : cards) {
            drawPile.add(Objects.requireNonNull(card, "牌组不能包含 null"));
        }
        Collections.shuffle(drawPile, random);
    }

    /**
     * 抽牌，直到手牌数达到 targetHandSize。
     *
     * @return 本次实际抽到的手牌快照
     */
    public List<CardInstance> drawToHandSize(int targetHandSize) {
        return draw(Math.max(0, targetHandSize - hand.size()));
    }

    /**
     * 从抽牌堆抽牌。抽牌堆为空时，会先把弃牌堆洗回抽牌堆。
     *
     * @return 本次实际抽到的牌，数量可能少于请求值
     */
    public List<CardInstance> draw(int count) {
        if (count <= 0) {
            return List.of();
        }

        List<CardInstance> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    break;
                }
                shuffleDiscardIntoDraw();
                logger.accept("抽牌堆为空，弃牌堆洗回抽牌堆。");
            }

            CardInstance instance = drawPile.remove(drawPile.size() - 1);
            hand.add(instance);
            drawn.add(instance);
        }
        return List.copyOf(drawn);
    }

    /** 弃掉当前全部手牌。 */
    public void discardHand() {
        discardPile.addAll(hand);
        hand.clear();
    }

    /** 从手牌中取出一张牌，交由出牌流程处理。 */
    public CardInstance removeFromHand(int handIndex) {
        return hand.remove(handIndex);
    }

    /** 查看手牌中指定位置的牌，不改变牌堆状态。 */
    public CardInstance peekHand(int handIndex) {
        return hand.get(handIndex);
    }

    /**
     * 升级手牌中的一张牌，并保留原实例编号。
     *
     * @param handIndex 要升级的手牌下标
     * @return 升级后的卡牌实例；目标不可升级或已经升级时返回 null
     */
    public CardInstance upgradeInHand(int handIndex) {
        if (handIndex < 0 || handIndex >= hand.size()) {
            return null;
        }
        CardInstance current = hand.get(handIndex);
        return upgradeIfPossible(current, handIndex);
    }

    /**
     * 按实例编号升级手牌中的一张牌，并保留原实例编号。
     *
     * @param cardInstanceId 要升级的手牌实例 id
     * @return 升级后的卡牌实例；找不到、不可升级或已经升级时返回 null
     */
    public CardInstance upgradeInHand(String cardInstanceId) {
        int handIndex = findHandIndex(cardInstanceId);
        if (handIndex < 0) {
            return null;
        }
        return upgradeIfPossible(hand.get(handIndex), handIndex);
    }

    private CardInstance upgradeIfPossible(CardInstance current, int handIndex) {
        if (current.upgraded() || !current.card().upgradable()) {
            return null;
        }
        CardInstance upgraded = current.upgradedCopy();
        hand.set(handIndex, upgraded);
        return upgraded;
    }

    /** 根据实例 id 查找手牌下标；找不到返回 -1。 */
    public int findHandIndex(String instanceId) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).id().equals(instanceId)) {
                return i;
            }
        }
        return -1;
    }

    /** 将打出的普通牌放入弃牌堆。 */
    public void sendToDiscard(CardInstance instance) {
        discardPile.add(instance);
    }

    /** 将带有「消耗」效果的牌放入消耗堆。 */
    public void sendToExhaust(CardInstance instance) {
        exhaustPile.add(instance);
    }

    /** 把一张牌放到抽牌堆顶部。 */
    public void putOnTopOfDrawPile(CardInstance instance) {
        drawPile.add(instance);
    }

    /** 将弃牌堆洗回抽牌堆。 */
    public void shuffleDiscardIntoDraw() {
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile, random);
    }

    public List<CardInstance> getHand() {
        return List.copyOf(hand);
    }

    public List<CardInstance> getDrawPile() {
        return List.copyOf(drawPile);
    }

    public List<CardInstance> getDiscardPile() {
        return List.copyOf(discardPile);
    }

    public List<CardInstance> getExhaustPile() {
        return List.copyOf(exhaustPile);
    }

    public int getHandSize() {
        return hand.size();
    }

    public int getDrawPileSize() {
        return drawPile.size();
    }

    public int getDiscardPileSize() {
        return discardPile.size();
    }

    public int getExhaustPileSize() {
        return exhaustPile.size();
    }
}
