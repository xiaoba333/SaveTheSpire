package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 战斗调度器：只管理回合生命周期，具体出牌与怪物 AI 交给下层服务。
 */
public class Combat {

    public static final int PLAYER_MAX_HP = 50;
    public static final int MONSTER_MAX_HP = 30;
    public static final int MONSTER_ATTACK = 10;
    public static final int MONSTER_BLOCK = 10;
    public static final int HAND_SIZE = 5;
    public static final int PLAYER_MAX_ENERGY = 3;

    private final BattleState state;
    private final BattleEventBus eventBus;
    private final CardPlayService cardPlayService;
    private final MonsterAiService monsterAi;
    private final Consumer<String> logger;
    private final List<String> newLogs = new ArrayList<>();

    /**
     * 保留给原有调试界面和单元测试的独立战斗构造方法。
     */
    public Combat(Consumer<String> logger) {
        this(
                new Player(PLAYER_MAX_HP, PLAYER_MAX_ENERGY),
                createDefaultDeck(),
                logger,
                result -> { },
                upgradedCard -> { },
                MonsterAiService.regular());
    }

    /**
     * 创建与本局共享状态连接的战斗。
     *
     * <p>玩家对象直接来自 RunState，因此战斗中的生命变化会保留到后续关卡。
     * 永久牌组只用于初始化本场战斗的临时牌堆。</p>
     */
    public Combat(
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler) {
        this(player, battleDeck, logger, finishHandler, upgradedCard -> { });
    }

    /**
     * 创建与本局共享状态连接、并同步永久牌组升级的战斗。
     *
     * @param cardUpgradeHandler 当锻造牌升级牌实例时，把升级结果同步回 RunState
     */
    public Combat(
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler,
            Consumer<CardInstance> cardUpgradeHandler) {
        this(
                player,
                battleDeck,
                logger,
                finishHandler,
                cardUpgradeHandler,
                MonsterAiService.regular());
    }

    /**
     * 完整装配：可注入怪物 AI（普通怪 / Boss）。
     */
    public Combat(
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler,
            Consumer<CardInstance> cardUpgradeHandler,
            MonsterAiService monsterAi) {
        Objects.requireNonNull(player, "玩家不能为 null");
        Objects.requireNonNull(battleDeck, "战斗牌组不能为 null");
        Objects.requireNonNull(finishHandler, "关卡结束处理器不能为 null");
        Objects.requireNonNull(cardUpgradeHandler, "卡牌升级处理器不能为 null");
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.monsterAi = Objects.requireNonNull(monsterAi, "怪物 AI 不能为 null");
        this.state = new BattleState(
                player, battleDeck, new CardPiles(logger), MONSTER_MAX_HP);
        this.eventBus = new BattleEventBus();
        this.eventBus.subscribeFinished(finishHandler);
        this.cardPlayService = new CardPlayService(this::log, cardUpgradeHandler);
        startNewFight();
    }

    public int getPlayerHp() {
        return state.getPlayer().getHealth();
    }

    public int getPlayerBlock() {
        return state.getPlayer().getArmor();
    }

    public int getMonsterHp() {
        return state.getMonsterHp();
    }

    public int getMonsterBlock() {
        return state.getMonsterBlock();
    }

    public int getEnergy() {
        return state.getPlayer().getEnergy();
    }

    public int getPlayerMaxHp() {
        return state.getPlayer().getMaxHealth();
    }

    public int getPlayerMaxEnergy() {
        return state.getPlayer().getMaxEnergy();
    }

    public int getMonsterMaxHp() {
        return state.getMonsterMaxHp();
    }

    public int getTurnNumber() {
        return state.getTurnNumber();
    }

    public int getDrawPileSize() {
        return state.getPiles().getDrawPileSize();
    }

    public int getDiscardPileSize() {
        return state.getPiles().getDiscardPileSize();
    }

    public int getExhaustPileSize() {
        return state.getPiles().getExhaustPileSize();
    }

    public boolean isPlayerTurn() {
        return state.isPlayerTurn() && !state.isFinished();
    }

    public boolean isFinished() {
        return state.isFinished();
    }

    public String getResultText() {
        return state.getResultText();
    }

    /**
     * 返回协议层可直接使用的胜负结果。
     *
     * @return "VICTORY"、"DEFEAT" 或 null
     */
    public String getResult() {
        return state.getResultCode();
    }

    /**
     * 返回当前战斗阶段。HTTP 接口采用同步结束回合，因此不需要暴露 MONSTER_TURN。
     */
    public String getPhase() {
        if (state.getResultCode() != null) {
            return state.getResultCode();
        }
        return "PLAYER_TURN";
    }

    public List<CardInstance> getHand() {
        return state.getPiles().getHand();
    }

    /** 抽牌堆快照，仅供调试界面查看。下一张在列表末尾。 */
    public List<Card> getDrawPile() {
        return state.getPiles().getDrawPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /** 弃牌堆快照，仅供调试界面查看。最近弃入的在列表末尾。 */
    public List<Card> getDiscardPile() {
        return state.getPiles().getDiscardPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /** 界面展示怪物下一动，方便看清攻防循环。 */
    public String getMonsterIntent() {
        return monsterAi.intentText(state);
    }

    /** 结构化怪物意图，供 HTTP 层序列化为 JSON。 */
    public Intent getMonsterIntentInfo() {
        MonsterAiService.IntentSnapshot snapshot = monsterAi.intentInfo(state);
        if (snapshot == null) {
            return null;
        }
        return new Intent(snapshot.type(), snapshot.value());
    }

    /**
     * 取走并清空本次操作产生的增量日志。
     */
    public List<String> drainNewLogs() {
        List<String> snapshot = new ArrayList<>(newLogs);
        newLogs.clear();
        return List.copyOf(snapshot);
    }

    /**
     * 点击手牌时调用。只能在玩家回合打出。
     */
    public PlayCardResult playCard(int handIndex) {
        PlayCardResult result = cardPlayService.play(state, handIndex);
        if (result == PlayCardResult.SUCCESS) {
            checkFinished();
        }
        return result;
    }

    /**
     * 供 HTTP 等外部调用方使用的出牌入口，按牌实例 id 出牌。
     */
    public PlayCardResult playCard(String cardInstanceId) {
        PlayCardResult result = cardPlayService.play(state, cardInstanceId);
        if (result == PlayCardResult.SUCCESS) {
            checkFinished();
        }
        return result;
    }

    /**
     * 玩家结束回合：弃掉剩余手牌，怪物行动，再进入玩家下一回合。
     */
    public void endPlayerTurn() {
        if (!isPlayerTurn()) {
            return;
        }

        state.getPiles().discardHand();
        log("玩家结束回合。");

        if (state.isFinished()) {
            return;
        }

        MonsterAiService.MonsterTurnResult monsterResult = monsterAi.executeTurn(state);
        if (monsterResult.attacked()) {
            log("怪物攻击，对玩家造成 " + monsterResult.value() + " 点伤害。");
        } else {
            log("怪物防御，获得 " + monsterResult.value() + " 点护盾。");
        }
        checkFinished();
        if (state.isFinished()) {
            return;
        }

        state.setTurnNumber(state.getTurnNumber() + 1);
        beginPlayerTurn();
    }

    private void startNewFight() {
        Player player = state.getPlayer();
        player.clearArmor();
        player.clearStatuses();
        player.refresh();
        state.setMonsterHp(state.getMonsterMaxHp());
        state.setMonsterBlock(0);
        state.setMonsterWillAttack(true);
        state.setFinished(false);
        state.setResultText("");
        state.setResultCode(null);
        state.setTurnNumber(1);
        newLogs.clear();
        state.getPiles().initializeInstances(state.getBattleDeck());

        log("战斗开始。玩家 HP " + player.getHealth()
                + "，怪物 HP " + state.getMonsterHp() + "。");
        checkFinished();
        if (state.isFinished()) {
            return;
        }
        beginPlayerTurn();
    }

    /** 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。 */
    private void beginPlayerTurn() {
        Player player = state.getPlayer();
        state.setPlayerTurn(true);
        player.clearArmor();
        player.refresh();
        state.getPiles().drawToHandSize(HAND_SIZE);
        log("—— 玩家回合 —— 能量 " + player.getEnergy()
                + "，抽牌 " + state.getPiles().getHandSize() + " 张。");
    }

    private void checkFinished() {
        if (state.isFinished()) {
            return;
        }
        if (state.getMonsterHp() <= 0) {
            state.setFinished(true);
            state.setPlayerTurn(false);
            state.setResultText("胜利：怪物血量已归零。");
            state.setResultCode("VICTORY");
            log(state.getResultText());
            eventBus.publishFinished(LevelResult.COMPLETED);
        } else if (state.getPlayer().isDead()) {
            state.setFinished(true);
            state.setPlayerTurn(false);
            state.setResultText("失败：玩家血量已归零。");
            state.setResultCode("DEFEAT");
            log(state.getResultText());
            eventBus.publishFinished(LevelResult.DEFEATED);
        }
    }

    /**
     * 向外部日志和增量日志写入一行战斗日志。
     */
    private void log(String line) {
        logger.accept(line);
        newLogs.add(line);
    }

    /**
     * 怪物下一回合意图。
     */
    public record Intent(String type, int value) {
    }

    private static List<CardInstance> createDefaultDeck() {
        return CardLibrary.startingDeck().stream()
                .map(card -> new CardInstance(UUID.randomUUID().toString(), card))
                .toList();
    }
}
