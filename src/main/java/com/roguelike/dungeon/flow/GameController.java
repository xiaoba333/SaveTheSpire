package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.CombatFactory;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.map.MapService;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.reward.RewardService;
import com.roguelike.dungeon.game.run.RunState;
import com.roguelike.dungeon.game.shop.ShopActionResult;
import com.roguelike.dungeon.game.shop.ShopItem;
import com.roguelike.dungeon.game.shop.ShopService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 连接单局状态、地图、关卡和战斗奖励的主流程控制器。
 *
 * <p>它不依赖 JavaFX 或 Unity。界面只需要读取 {@link #getPhase()}，
 * 再调用当前阶段允许的方法。</p>
 */
public final class GameController implements LevelFinishHandler {
    public static final int BATTLE_GOLD_REWARD = 20;
    public static final int ELITE_GOLD_REWARD = 35;

    private final RunState runState;
    private final List<Card> rewardPool;
    private final Consumer<String> combatLogger;

    private GamePhase phase = GamePhase.MAP;
    private Combat currentCombat;
    private RewardService currentReward;
    private ShopService currentShop;

    public GameController(
            RunState runState,
            List<Card> rewardPool,
            Consumer<String> combatLogger) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.rewardPool = List.copyOf(Objects.requireNonNull(
                rewardPool, "奖励卡池不能为 null"));
        this.rewardPool.forEach(card -> Objects.requireNonNull(
                card, "奖励卡池不能包含 null"));
        this.combatLogger = Objects.requireNonNull(
                combatLogger, "战斗日志处理器不能为 null");
    }

    public GamePhase getPhase() {
        return phase;
    }

    public RunState getRunState() {
        return runState;
    }

    public MapService getMapService() {
        return runState.getMapService();
    }

    public Optional<MapNode> getCurrentNode() {
        return getMapService().getCurrentNode();
    }

    /** 当前战斗；非战斗阶段时为空。 */
    public Optional<Combat> getCurrentCombat() {
        return Optional.ofNullable(currentCombat);
    }

    /** 当前待领取的战斗奖励；非奖励阶段时为空。 */
    public Optional<BattleReward> getCurrentReward() {
        return currentReward == null
                ? Optional.empty()
                : Optional.of(currentReward.getReward());
    }

    /** 当前商店尚未售出的卡牌商品。 */
    public List<ShopItem> getCurrentShopItems() {
        return currentShop == null ? List.of() : currentShop.getAvailableItems();
    }

    /** 当前商店可以选择删除的永久牌组。 */
    public List<CardInstance> getShopRemovableCards() {
        return currentShop == null ? List.of() : currentShop.getRemovableCards();
    }

    public boolean isShopCardRemovalUsed() {
        return currentShop != null && currentShop.isCardRemovalUsed();
    }

    /**
     * 在地图阶段选择一个可达节点，并进入对应关卡。
     */
    public MapNode selectNode(int nodeId) {
        requirePhase(GamePhase.MAP);
        MapNode node = getMapService().selectNode(nodeId);

        switch (node.type()) {
            case BATTLE, ELITE, BOSS -> startBattle();
            case EVENT -> phase = GamePhase.EVENT;
            case SHOP -> startShop(node);
            case REST -> phase = GamePhase.REST;
        }
        return node;
    }

    /**
     * 接收当前关卡的结束通知。战斗模块会自动调用；
     * 事件、商店和休息模块在自身流程结束时调用。
     */
    @Override
    public void onLevelFinished(LevelResult result) {
        Objects.requireNonNull(result, "关卡结果不能为 null");
        switch (phase) {
            case BATTLE -> finishBattle(result);
            case EVENT, SHOP, REST -> finishNonBattleLevel(result);
            default -> throw new IllegalStateException(
                    "当前阶段不能结束关卡: " + phase);
        }
    }

    /** 选择一张卡并领取当前战斗奖励。 */
    public void claimRewardCard(String cardDefinitionId) {
        requirePhase(GamePhase.REWARD);
        currentReward.claimCard(cardDefinitionId);
        finishReward();
    }

    /** 跳过卡牌选择，只领取金币。 */
    public void skipRewardCard() {
        requirePhase(GamePhase.REWARD);
        currentReward.skipCard();
        finishReward();
    }

    /** 在当前商店购买卡牌。 */
    public ShopActionResult buyShopItem(String itemId) {
        requirePhase(GamePhase.SHOP);
        return currentShop.buy(itemId);
    }

    /** 在当前商店删除一张永久牌组中的卡牌。 */
    public ShopActionResult removeCardAtShop(String cardInstanceId) {
        requirePhase(GamePhase.SHOP);
        return currentShop.removeCard(cardInstanceId);
    }

    /** 离开当前商店，完成并解锁地图节点。 */
    public void leaveShop() {
        requirePhase(GamePhase.SHOP);
        currentShop.leave();
    }

    private void startBattle() {
        phase = GamePhase.BATTLE;
        MapNode node = requireCurrentNode();
        currentCombat = CombatFactory.createForNode(
                node.type(),
                runState.getPlayer(),
                runState.getDeck(),
                combatLogger,
                this,
                runState::upgradeCard);
    }

    private void startShop(MapNode node) {
        phase = GamePhase.SHOP;
        currentShop = new ShopService(
                runState,
                rewardPool,
                shopSeed(node),
                this);
    }

    private void finishBattle(LevelResult result) {
        if (result == LevelResult.DEFEATED) {
            phase = GamePhase.DEFEAT;
            return;
        }

        MapNode node = requireCurrentNode();
        if (node.type() == MapNodeType.BOSS) {
            currentCombat = null;
            getMapService().completeCurrentNode();
            if (runState.hasNextAct()) {
                runState.advanceAct();
                phase = GamePhase.MAP;
            } else {
                phase = GamePhase.VICTORY;
            }
            return;
        }

        if (node.type() != MapNodeType.BATTLE && node.type() != MapNodeType.ELITE) {
            throw new IllegalStateException("非战斗节点进入了战斗结算: " + node.type());
        }

        int gold = node.type() == MapNodeType.ELITE
                ? ELITE_GOLD_REWARD
                : BATTLE_GOLD_REWARD;
        currentReward = new RewardService(
                runState,
                rewardPool,
                rewardSeed(node),
                gold);
        currentCombat = null;
        phase = GamePhase.REWARD;
    }

    private void finishNonBattleLevel(LevelResult result) {
        currentShop = null;
        if (result == LevelResult.DEFEATED) {
            phase = GamePhase.DEFEAT;
            return;
        }
        getMapService().completeCurrentNode();
        phase = GamePhase.MAP;
    }

    private void finishReward() {
        getMapService().completeCurrentNode();
        currentReward = null;
        phase = GamePhase.MAP;
    }

    private MapNode requireCurrentNode() {
        return getCurrentNode().orElseThrow(() ->
                new IllegalStateException("当前没有正在进行的地图节点"));
    }

    private long rewardSeed(MapNode node) {
        long seed = runState.getRunSeed();
        seed = seed * 31 + runState.getCurrentAct();
        return seed * 31 + node.id();
    }

    private long shopSeed(MapNode node) {
        return rewardSeed(node) ^ 0x5DEECE66DL;
    }

    private void requirePhase(GamePhase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "当前阶段为 " + phase + "，期望阶段为 " + expected);
        }
    }
}
