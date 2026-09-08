package com.roguelike.dungeon.game.deck;

import com.roguelike.dungeon.game.card.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 管理一场战斗中的四类卡牌区域：抽牌堆、手牌、弃牌堆、消耗堆。
 */
public final class CardPiles {

    private final Random random;
    private final Consumer<String> logger;
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<Card> exhaustPile = new ArrayList<>();

    public CardPiles(Consumer<String> logger) {
        this(logger, new Random());
    }

    public CardPiles(Consumer<String> logger, Random random) {
        this.logger = logger;
        this.random = random;
    }

    /** 清空所有区域，并装入一套已洗牌的初始牌组。 */
    public void initialize(List<Card> cards) {
        drawPile.clear();
        hand.clear();
        discardPile.clear();
        exhaustPile.clear();
        drawPile.addAll(cards);
        Collections.shuffle(drawPile, random);
    }

    /**
     * 抽牌，直到手牌数达到 targetHandSize。
     *
     * @return 本次实际抽到的手牌快照
     */
    public List<Card> drawToHandSize(int targetHandSize) {
        return draw(Math.max(0, targetHandSize - hand.size()));
    }

    /**
     * 从抽牌堆抽牌。抽牌堆为空时，会先把弃牌堆洗回抽牌堆。
     *
     * @return 本次实际抽到的牌，数量可能少于请求值
     */
    public List<Card> draw(int count) {
        if (count <= 0) {
            return List.of();
        }

        List<Card> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    break;
                }
                shuffleDiscardIntoDraw();
                logger.accept("抽牌堆为空，弃牌堆洗回抽牌堆。");
            }

            Card card = drawPile.remove(drawPile.size() - 1);
            hand.add(card);
            drawn.add(card);
        }
        return List.copyOf(drawn);
    }

    /** 弃掉当前全部手牌。 */
    public void discardHand() {
        discardPile.addAll(hand);
        hand.clear();
    }

    /** 从手牌中取出一张牌，交由出牌流程处理。 */
    public Card removeFromHand(int handIndex) {
        return hand.remove(handIndex);
    }

    /** 查看手牌中指定位置的牌，不改变牌堆状态。 */
    public Card peekHand(int handIndex) {
        return hand.get(handIndex);
    }

    /** 将打出的普通牌放入弃牌堆。 */
    public void sendToDiscard(Card card) {
        discardPile.add(card);
    }

    /** 将带有「消耗」效果的牌放入消耗堆。 */
    public void sendToExhaust(Card card) {
        exhaustPile.add(card);
    }

    /** 把一张牌放到抽牌堆顶部。 */
    public void putOnTopOfDrawPile(Card card) {
        drawPile.add(card);
    }

    /** 将弃牌堆洗回抽牌堆。 */
    public void shuffleDiscardIntoDraw() {
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile, random);
    }

    public List<Card> getHand() {
        return List.copyOf(hand);
    }

    public List<Card> getDrawPile() {
        return List.copyOf(drawPile);
    }

    public List<Card> getDiscardPile() {
        return List.copyOf(discardPile);
    }

    public List<Card> getExhaustPile() {
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
