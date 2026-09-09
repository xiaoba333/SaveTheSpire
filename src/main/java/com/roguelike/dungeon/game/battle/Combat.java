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
 * 杀戮尖塔风格的最小战斗规则：抽牌、能量、出牌、结束回合、怪物攻防交替、护盾抵伤。
 */
public class Combat {

    public static final int PLAYER_MAX_HP = 50;
    public static final int MONSTER_MAX_HP = 30;
    public static final int MONSTER_ATTACK = 10;
    public static final int MONSTER_BLOCK = 10;
    public static final int HAND_SIZE = 5;
    public static final int PLAYER_MAX_ENERGY = 3;

    private final Consumer<String> logger;
    private final CardPiles piles;
    private final Player player;
    private final List<CardInstance> battleDeck;
    private final LevelFinishHandler finishHandler;

    private int monsterHp;
    private int monsterBlock;
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText;
    private String resultCode;
    private int turnNumber;
    private boolean finishNotified;
    private final List<String> newLogs = new ArrayList<>();

    /**
     * 保留给原有调试界面和单元测试的独立战斗构造方法。
     */
    public Combat(Consumer<String> logger) {
        this(
                new Player(PLAYER_MAX_HP, PLAYER_MAX_ENERGY),
                createDefaultDeck(),
                logger,
                result -> { });
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
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        this.battleDeck = List.copyOf(Objects.requireNonNull(
                battleDeck, "战斗牌组不能为 null"));
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
        this.finishHandler = Objects.requireNonNull(
                finishHandler, "关卡结束处理器不能为 null");
        this.piles = new CardPiles(logger);
        startNewFight();
    }

    public int getPlayerHp() {
        return player.getHealth();
    }

    public int getPlayerBlock() {
        return player.getArmor();
    }

    public int getMonsterHp() {
        return monsterHp;
    }

    public int getMonsterBlock() {
        return monsterBlock;
    }

    public int getEnergy() {
        return player.getEnergy();
    }

    public int getPlayerMaxHp() {
        return player.getMaxHealth();
    }

    public int getPlayerMaxEnergy() {
        return player.getMaxEnergy();
    }

    public int getMonsterMaxHp() {
        return MONSTER_MAX_HP;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public int getDrawPileSize() {
        return piles.getDrawPileSize();
    }

    public int getDiscardPileSize() {
        return piles.getDiscardPileSize();
    }

    public int getExhaustPileSize() {
        return piles.getExhaustPileSize();
    }

    public boolean isPlayerTurn() {
        return playerTurn && !finished;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getResultText() {
        return resultText;
    }

    /**
     * 返回协议层可直接使用的胜负结果。
     *
     * @return "VICTORY"、"DEFEAT" 或 null
     */
    public String getResult() {
        return resultCode;
    }

    /**
     * 返回当前战斗阶段。HTTP 接口采用同步结束回合，因此不需要暴露 MONSTER_TURN。
     */
    public String getPhase() {
        if (resultCode != null) {
            return resultCode;
        }
        return "PLAYER_TURN";
    }

    public List<CardInstance> getHand() {
        return piles.getHand();
    }

    /** 抽牌堆快照，仅供调试界面查看。下一张在列表末尾。 */
    public List<Card> getDrawPile() {
        return piles.getDrawPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /** 弃牌堆快照，仅供调试界面查看。最近弃入的在列表末尾。 */
    public List<Card> getDiscardPile() {
        return piles.getDiscardPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /** 界面展示怪物下一动，方便看清攻防循环。 */
    public String getMonsterIntent() {
        if (finished) {
            return "已倒下";
        }
        return monsterWillAttack ? "下回合：攻击 " + MONSTER_ATTACK : "下回合：防御 +" + MONSTER_BLOCK;
    }

    /** 结构化怪物意图，供 HTTP 层序列化为 JSON。 */
    public Intent getMonsterIntentInfo() {
        if (finished) {
            return null;
        }
        return monsterWillAttack
                ? new Intent("ATTACK", MONSTER_ATTACK)
                : new Intent("DEFEND", MONSTER_BLOCK);
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
        if (finished) {
            return PlayCardResult.BATTLE_FINISHED;
        }
        if (!playerTurn) {
            return PlayCardResult.NOT_PLAYER_TURN;
        }
        if (handIndex < 0 || handIndex >= piles.getHandSize()) {
            return PlayCardResult.INVALID_CARD;
        }
        return playCardInternal(handIndex);
    }

    /**
     * 供 HTTP 等外部调用方使用的出牌入口，按牌实例 id 出牌。
     */
    public PlayCardResult playCard(String cardInstanceId) {
        if (finished) {
            return PlayCardResult.BATTLE_FINISHED;
        }
        if (!playerTurn) {
            return PlayCardResult.NOT_PLAYER_TURN;
        }
        int handIndex = piles.findHandIndex(cardInstanceId);
        if (handIndex < 0) {
            return PlayCardResult.INVALID_CARD;
        }
        return playCardInternal(handIndex);
    }

    private PlayCardResult playCardInternal(int handIndex) {
        CardInstance instance = piles.peekHand(handIndex);
        Card card = instance.card();
        if (!card.playable()) {
            log("「" + card.name() + "」无法打出。");
            return PlayCardResult.CARD_NOT_PLAYABLE;
        }

        if (!tryConsumeEnergy(card.cost())) {
            log("能量不足，无法打出「" + card.name() + "」。");
            return PlayCardResult.NOT_ENOUGH_ENERGY;
        }

        piles.removeFromHand(handIndex);
        log("玩家打出「" + card.name() + "」，消耗 " + card.cost() + " 点能量。");
        card.effect().apply(new CombatCardEffectContext(this, player, piles));

        if (card.exhausts()) {
            piles.sendToExhaust(instance);
            log("「" + card.name() + "」已消耗。");
        } else {
            piles.sendToDiscard(instance);
        }
        checkFinished();
        return PlayCardResult.SUCCESS;
    }

    /**
     * 玩家结束回合：弃掉剩余手牌，怪物行动，再进入玩家下一回合。
     */
    public void endPlayerTurn() {
        if (!isPlayerTurn()) {
            return;
        }

        piles.discardHand();
        log("玩家结束回合。");

        if (finished) {
            return;
        }

        runMonsterTurn();
        if (finished) {
            return;
        }

        turnNumber++;
        beginPlayerTurn();
    }

    private void startNewFight() {
        player.clearArmor();
        player.clearStatuses();
        player.refresh();
        monsterHp = MONSTER_MAX_HP;
        monsterBlock = 0;
        monsterWillAttack = true;
        finished = false;
        resultText = "";
        resultCode = null;
        turnNumber = 1;
        finishNotified = false;
        newLogs.clear();
        piles.initializeInstances(battleDeck);

        log("战斗开始。玩家 HP " + player.getHealth()
                + "，怪物 HP " + monsterHp + "。");
        checkFinished();
        if (finished) {
            return;
        }
        beginPlayerTurn();
    }

    /** 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。 */
    private void beginPlayerTurn() {
        playerTurn = true;
        player.clearArmor();
        player.refresh();
        drawToHandSize();
        log("—— 玩家回合 —— 能量 " + player.getEnergy()
                + "，抽牌 " + piles.getHandSize() + " 张。");
    }

    private void runMonsterTurn() {
        playerTurn = false;
        // 怪物回合开始时清空自己剩余护盾，本回合再决定攻击或叠盾。
        monsterBlock = 0;

        if (monsterWillAttack) {
            int dealt = applyDamage(false, MONSTER_ATTACK);
            log("怪物攻击，对玩家造成 " + dealt + " 点伤害。");
        } else {
            monsterBlock += MONSTER_BLOCK;
            log("怪物防御，获得 " + MONSTER_BLOCK + " 点护盾。");
        }
        monsterWillAttack = !monsterWillAttack;
        checkFinished();
    }

    /**
     * @param toMonster true 表示伤害打向怪物，false 表示打向玩家
     * @return 实际扣掉的血量（护盾先抵消）
     */
    /**
     * 结算一次伤害。
     *
     * <p>package-private 是为了让同包的卡牌效果上下文可以使用；
     * 外部模块仍应通过 Combat 的公开方法操作战斗。</p>
     *
     * @param toMonster true 表示伤害打向怪物，false 表示打向玩家
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    int applyDamage(boolean toMonster, int amount) {
        if (toMonster) {
            int absorbed = Math.min(monsterBlock, amount);
            monsterBlock -= absorbed;
            int hpLoss = amount - absorbed;
            monsterHp = Math.max(0, monsterHp - hpLoss);
            return hpLoss;
        }
        return player.receiveDamage(amount);
    }

    private void drawToHandSize() {
        piles.drawToHandSize(HAND_SIZE);
    }

    private boolean tryConsumeEnergy(int cost) {
        if (cost < 0) {
            return false;
        }
        return player.consume(cost);
    }

    private void checkFinished() {
        if (finished) {
            return;
        }
        if (monsterHp <= 0) {
            finished = true;
            playerTurn = false;
            resultText = "胜利：怪物血量已归零。";
            resultCode = "VICTORY";
            log(resultText);
            notifyFinished(LevelResult.COMPLETED);
        } else if (player.isDead()) {
            finished = true;
            playerTurn = false;
            resultText = "失败：玩家血量已归零。";
            resultCode = "DEFEAT";
            log(resultText);
            notifyFinished(LevelResult.DEFEATED);
        }
    }

    private void notifyFinished(LevelResult result) {
        if (finishNotified) {
            return;
        }
        finishNotified = true;
        finishHandler.onLevelFinished(result);
    }

    /**
     * 向外部日志和增量日志写入一行战斗日志。
     *
     * <p>同包卡牌效果上下文会调用这个方法，避免直接持有日志列表。</p>
     */
    void log(String line) {
        logger.accept(line);
        newLogs.add(line);
    }

    /**
     * 给怪物增加护甲。
     *
     * <p>这个方法供同包卡牌效果上下文调用，外部逻辑仍通过卡牌效果进入。</p>
     */
    void addMonsterBlockInternal(int amount) {
        if (amount <= 0) {
            return;
        }
        monsterBlock += amount;
    }

    /**
     * 怪物下一回合意图。
     */
    public record Intent(String type, int value) {
    }

    /**
     * 出牌结果。HTTP 层可以直接根据此枚举映射错误码。
     */
    public enum PlayCardResult {
        SUCCESS,
        NOT_PLAYER_TURN,
        INVALID_CARD,
        NOT_ENOUGH_ENERGY,
        CARD_NOT_PLAYABLE,
        BATTLE_FINISHED
    }

    private static List<CardInstance> createDefaultDeck() {
        return CardLibrary.startingDeck().stream()
                .map(card -> new CardInstance(UUID.randomUUID().toString(), card))
                .toList();
    }
}
