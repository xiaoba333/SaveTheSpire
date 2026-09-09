package com.roguelike.dungeon.game.run;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一局游戏中跨关卡共享的权威状态。
 *
 * <p>玩家生命、永久牌组、金币、章节和地图保存在这里。能量、护甲、手牌、
 * 抽牌堆、弃牌堆和敌人状态属于单场战斗临时数据，战斗结束后应重置，
 * 不作为跨关卡进度保留。</p>
 */
public final class RunState {
    private final Player player;
    private final List<CardInstance> deck;
    private final long runSeed;
    private final int totalActs;

    private int gold;
    private int currentAct;
    private MapService mapService;

    /**
     * 创建一局新游戏。
     *
     * @param player 本局唯一的玩家对象
     * @param startingDeck 初始永久牌组
     * @param startingGold 初始金币，不能为负数
     * @param runSeed 本局随机种子
     * @param totalActs 总章节数，至少为 1
     */
    public RunState(
            Player player,
            List<CardInstance> startingDeck,
            int startingGold,
            long runSeed,
            int totalActs) {
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        if (startingGold < 0) {
            throw new IllegalArgumentException("初始金币不能为负数");
        }
        if (totalActs <= 0) {
            throw new IllegalArgumentException("总章节数至少为 1");
        }

        this.deck = copyAndValidateDeck(startingDeck);
        this.gold = startingGold;
        this.runSeed = runSeed;
        this.totalActs = totalActs;
        this.currentAct = 1;
        this.mapService = new MapService(seedForAct(currentAct));
    }

    /** 返回本局唯一的玩家对象。 */
    public Player getPlayer() {
        return player;
    }

    /** 返回永久牌组的不可变快照。 */
    public List<CardInstance> getDeck() {
        return List.copyOf(deck);
    }

    /** 向永久牌组添加一张实例编号唯一的卡牌。 */
    public void addCard(CardInstance cardInstance) {
        validateCardInstance(cardInstance);
        boolean duplicateId = deck.stream()
                .anyMatch(card -> card.id().equals(cardInstance.id()));
        if (duplicateId) {
            throw new IllegalArgumentException("卡牌实例编号已存在: " + cardInstance.id());
        }
        deck.add(cardInstance);
    }

    /**
     * 按实例编号从永久牌组移除一张卡牌。
     *
     * @return 找到并移除时返回 true，否则返回 false
     */
    public boolean removeCard(String cardInstanceId) {
        if (cardInstanceId == null || cardInstanceId.isBlank()) {
            return false;
        }
        return deck.removeIf(card -> card.id().equals(cardInstanceId));
    }

    public int getGold() {
        return gold;
    }

    /** 增加金币；amount 小于等于 0 时忽略。 */
    public void addGold(int amount) {
        if (amount <= 0) {
            return;
        }
        gold = Math.addExact(gold, amount);
    }

    /**
     * 原子扣除金币。
     *
     * @return 金币足够并完成扣除时返回 true；金币不足或 amount 为负数时返回 false
     */
    public boolean spendGold(int amount) {
        if (amount < 0 || gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    public long getRunSeed() {
        return runSeed;
    }

    public int getCurrentAct() {
        return currentAct;
    }

    public int getTotalActs() {
        return totalActs;
    }

    public boolean hasNextAct() {
        return currentAct < totalActs;
    }

    /**
     * 推进到下一章节，并为新章节创建地图。
     *
     * @throws IllegalStateException 当前已经是最后一章
     */
    public void advanceAct() {
        if (!hasNextAct()) {
            throw new IllegalStateException("当前已经是最后一章");
        }
        currentAct++;
        mapService = new MapService(seedForAct(currentAct));
    }

    /** 返回当前章节的地图逻辑服务。 */
    public MapService getMapService() {
        return mapService;
    }

    private long seedForAct(int act) {
        return runSeed + act - 1L;
    }

    private static List<CardInstance> copyAndValidateDeck(List<CardInstance> source) {
        Objects.requireNonNull(source, "初始牌组不能为 null");
        List<CardInstance> copy = new ArrayList<>(source.size());
        Set<String> instanceIds = new HashSet<>();
        for (CardInstance cardInstance : source) {
            validateCardInstance(cardInstance);
            if (!instanceIds.add(cardInstance.id())) {
                throw new IllegalArgumentException("初始牌组存在重复实例编号: " + cardInstance.id());
            }
            copy.add(cardInstance);
        }
        return copy;
    }

    private static void validateCardInstance(CardInstance cardInstance) {
        Objects.requireNonNull(cardInstance, "卡牌实例不能为 null");
        if (cardInstance.id() == null || cardInstance.id().isBlank()) {
            throw new IllegalArgumentException("卡牌实例编号不能为空");
        }
        Objects.requireNonNull(cardInstance.card(), "卡牌定义不能为 null");
    }
}
