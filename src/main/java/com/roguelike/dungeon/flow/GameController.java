package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.CombatFactory;
import com.roguelike.dungeon.game.battle.MonsterAi;
import com.roguelike.dungeon.game.battle.MonsterCatalog;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.campfire.CampfireAction;
import com.roguelike.dungeon.game.campfire.CampfireActionResult;
import com.roguelike.dungeon.game.campfire.CampfireService;
import com.roguelike.dungeon.game.event.EventCatalog;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.event.EventChoiceResult;
import com.roguelike.dungeon.game.event.EventService;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.event.GameEvent;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.map.MapService;
import com.roguelike.dungeon.game.relic.RelicLibrary;
import com.roguelike.dungeon.game.relic.RelicService;
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
    /** 本局共享的遗物分发器：开局发放初始遗物，战斗与奖励都通过它结算。 */
    private final RelicService relicService;

    private GamePhase phase = GamePhase.MAP;
    private Combat currentCombat;
    private RewardService currentReward;
    private EventService currentEvent;
    private CampfireService currentCampfire;
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
        this.relicService = new RelicService(
                this.runState.getPlayer(), this.combatLogger);
        grantStartingRelics();
    }

    /**
     * 开局发放初始遗物。
     *
     * <p>每个遗物都是纯增益，目的是让开局有稳定战力，
     * 避免「一件遗物都没有、被怪物两下打死」的体验。</p>
     */
    private void grantStartingRelics() {
        for (Relic relic : RelicLibrary.createStarting()) {
            relicService.acquire(relic);
        }
    }

    /** 本局共享的遗物分发器，供界面或测试查询玩家持有的遗物。 */
    public RelicService getRelicService() {
        return relicService;
    }

    /** 玩家当前持有的遗物，按获得顺序。 */
    public List<Relic> getRelics() {
        return relicService.relics();
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

    /** 当前事件基础信息；不在事件阶段时为空。 */
    public Optional<GameEvent> getCurrentEvent() {
        return currentEvent == null
                ? Optional.empty()
                : Optional.of(currentEvent.getEvent());
    }

    /** 当前事件根据玩家状态计算出的选项。 */
    public List<EventChoice> getCurrentEventChoices() {
        return currentEvent == null ? List.of() : currentEvent.getChoices();
    }

    /** 当前篝火根据玩家状态计算出的操作。 */
    public List<CampfireAction> getCurrentCampfireActions() {
        return currentCampfire == null ? List.of() : currentCampfire.getActions();
    }

    /** 当前篝火可以选择锻造的永久牌组卡牌。 */
    public List<CardInstance> getCampfireUpgradeableCards() {
        return currentCampfire == null ? List.of() : currentCampfire.getUpgradeableCards();
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
case EVENT -> startEvent(node);
case SHOP -> startShop(node);
case REST -> startCampfire();
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

    /** 提交当前事件的一个选项。成功后事件节点会自动结算。 */
    public EventChoiceResult chooseEventChoice(String choiceId) {
        requirePhase(GamePhase.EVENT);
        return currentEvent.choose(choiceId);
    }

    /** 在当前篝火休息并结算节点。 */
    public CampfireActionResult restAtCampfire() {
        requirePhase(GamePhase.REST);
        return currentCampfire.rest();
    }

    /** 在当前篝火升级一张永久牌组卡牌并结算节点。 */
    public CampfireActionResult smithAtCampfire(String cardInstanceId) {
        requirePhase(GamePhase.REST);
        return currentCampfire.smith(cardInstanceId);
    }

    /** 不进行操作，离开当前篝火并结算节点。 */
    public CampfireActionResult leaveCampfire() {
        requirePhase(GamePhase.REST);
        return currentCampfire.leave();
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
                runState.getPlayer(),
                runState.getDeck(),
                combatLogger,
                this,
                runState::upgradeCard,
                pickMonster(node),
                relicService);
    }

    private void startEvent(MapNode node) {
        phase = GamePhase.EVENT;
        currentEvent = EventCatalog.openEvent(
                runState,
                eventSeed(node),
                this);
    }

    private void startCampfire() {
        phase = GamePhase.REST;
        currentCampfire = new CampfireService(runState, this);
    }

    /** 按节点类型挑选怪物：普通战斗按种子挑一只轻松怪，精英走普通 AI，Boss 走 Boss AI。 */
    private MonsterAi pickMonster(MapNode node) {
        return switch (node.type()) {
            case BATTLE -> MonsterCatalog.randomEasy(rewardSeed(node));
            case ELITE -> MonsterCatalog.randomElite(rewardSeed(node));
            case BOSS -> MonsterCatalog.randomBoss(rewardSeed(node));
            default -> throw new IllegalStateException(
                    "非战斗节点无法选取怪物: " + node.type());
        };
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
                gold,
                relicService,
                relicRaritiesFor(node.type()));
        currentCombat = null;
        phase = GamePhase.REWARD;
    }

    /**
     * 不同节点掉落的遗物稀有度。
     *
     * <p>普通战斗只掉普通 / 罕见，精英能掉稀有，避免开局就滚出太强的遗物。</p>
     */
    private static RelicRarity[] relicRaritiesFor(MapNodeType nodeType) {
        return nodeType == MapNodeType.ELITE
                ? new RelicRarity[]{
                        RelicRarity.COMMON, RelicRarity.UNCOMMON, RelicRarity.RARE}
                : new RelicRarity[]{RelicRarity.COMMON, RelicRarity.UNCOMMON};
    }

    private void finishNonBattleLevel(LevelResult result) {
        currentEvent = null;
        currentCampfire = null;
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

private long eventSeed(MapNode node) {
    return rewardSeed(node) ^ 0xC64A7935BD1E995L;
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
