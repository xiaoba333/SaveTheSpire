package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Enemy;
import com.roguelike.dungeon.game.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 杀戮尖塔风格的最小战斗规则：抽牌、能量、出牌、结束回合、怪物攻防交替、护盾抵伤。
 *
 * <p>这个类可以理解为战斗的“门面”，界面或 HTTP 层只调用这里公开的方法。
 * 真正的牌堆移动由 {@link CardPiles} 负责；卡牌具体效果由 {@code CardEffect}
 * 通过内部类 {@link CombatCardEffectContext} 调用本类暴露的战斗操作。</p>
 *
 * <p><b>本文件涉及的 Java 8 到 Java 21 语法</b></p>
 * <ul>
 *   <li>{@code Consumer<String>}：接收一个参数、不返回值的函数式接口；</li>
 *   <li>{@code stream().map(CardInstance::card).toList()}：Stream API、方法引用和不可变列表收集；</li>
 *   <li>{@code record Intent(...)}：类内部嵌套的不可变数据 record；</li>
 *   <li>{@code enum PlayCardResult}：用来表示出牌结果，便于外部映射错误码。</li>
 * </ul>
 */
public class Combat {

    // 以下常量只用于旧的无参构造方法，以及尚未迁移到 RunState 的旧 UI。
    // 正式爬塔流程应通过 EncounterDefinition、Player 和 Enemy 传入真实数值。
    public static final int PLAYER_MAX_HP = 50;
    public static final int MONSTER_MAX_HP = 30;
    public static final int MONSTER_ATTACK = 10;
    public static final int MONSTER_BLOCK = 10;
    public static final int HAND_SIZE = 5;
    public static final int PLAYER_MAX_ENERGY = 3;

    /**
     * 日志回调。
     *
     * <p>{@code Consumer<String>} 表示“接收一个 String、不返回结果”的操作。
     * 构造 Combat 时传入它，可以让战斗逻辑不依赖具体日志输出方式。</p>
     */
    private final Consumer<String> logger;

    /**
     * 本场战斗使用的玩家实体。
     *
     * <p>玩家是 RunState 中跨关卡复用的对象。战斗开始时只重置能量、护甲和状态，
     * 不会把生命值重置为满血。</p>
     */
    private final Player player;

    /**
     * 本场战斗面对的敌人实体。
     *
     * <p>敌人通常由地图节点对应的 EncounterDefinition 创建，每场战斗使用一个新实例。</p>
     */
    private final Enemy enemy;

    /** 牌堆管理器：负责抽牌堆、手牌、弃牌堆、消耗堆的移动。 */
    private final CardPiles piles;

    /** 本场战斗开始前准备的永久牌组实例。 */
    private final List<CardInstance> battleDeck;

    /** 敌人整场战斗的行动脚本，按顺序循环执行。 */
    private final List<MonsterAction> monsterActions;

    /** 下一轮怪物行动在 monsterActions 中的下标。 */
    private int monsterActionIndex;

    /** true 表示当前轮到玩家操作。 */
    private boolean playerTurn;
    /** true 表示战斗已经结束。 */
    private boolean finished;
    /** 战斗结束时用于界面展示的结果文本。 */
    private String resultText;
    /** 协议层使用的结果代码："VICTORY"、"DEFEAT"，未结束时为 null。 */
    private String resultCode;
    /** 当前回合数，从 1 开始。 */
    private int turnNumber;
    /** 本次操作新增的日志，供调用方一次性取走。 */
    private final List<String> newLogs = new ArrayList<>();

    /**
     * 创建并初始化一场新战斗。
     *
     * @param logger 接收战斗日志的回调，例如界面追加文本或测试保存到列表
     */
    public Combat(Consumer<String> logger) {
        this(
                logger,
                new Player(PLAYER_MAX_HP, PLAYER_MAX_ENERGY),
                new Enemy(MONSTER_MAX_HP),
                createInstances(CardLibrary.startingDeck()),
                EncounterDefinition.alternating(MONSTER_ATTACK, MONSTER_BLOCK));
    }

    /**
     * 使用默认怪物脚本创建一场战斗。
     *
     * @param logger 日志回调
     * @param player 本局跨关卡复用的玩家对象
     * @param enemy 本场战斗的敌人对象
     * @param deck 本场战斗使用的永久牌组实例
     */
    public Combat(
            Consumer<String> logger,
            Player player,
            Enemy enemy,
            List<CardInstance> deck) {
        this(
                logger,
                player,
                enemy,
                deck,
                EncounterDefinition.alternating(MONSTER_ATTACK, MONSTER_BLOCK));
    }

    /**
     * 正式战斗构造入口，由地图/关卡协调器创建。
     *
     * @param logger 日志回调
     * @param player 本局玩家实体
     * @param enemy 本场敌人实体
     * @param deck 永久牌组实例列表，来自 RunState
     * @param encounter 敌人行动脚本
     */
    public Combat(
            Consumer<String> logger,
            Player player,
            Enemy enemy,
            List<CardInstance> deck,
            EncounterDefinition encounter) {
        this.logger = logger;
        this.player = Objects.requireNonNull(player, "玩家实体不能为 null");
        this.enemy = Objects.requireNonNull(enemy, "敌人实体不能为 null");
        this.battleDeck = List.copyOf(Objects.requireNonNull(deck, "牌组不能为 null"));
        this.monsterActions = Objects.requireNonNull(
                encounter, "敌人行动配置不能为 null").actions();
        this.piles = new CardPiles(logger);
        startNewFight();
    }

    /** @return 玩家当前生命值 */
    public int getPlayerHp() {
        return player.getHealth();
    }

    /** @return 玩家当前护甲值 */
    public int getPlayerBlock() {
        return player.getArmor();
    }

    /** @return 怪物当前生命值 */
    public int getMonsterHp() {
        return enemy.getHealth();
    }

    /** @return 怪物当前护甲值 */
    public int getMonsterBlock() {
        return enemy.getArmor();
    }

    /** @return 玩家当前回合剩余能量 */
    public int getEnergy() {
        return player.getEnergy();
    }

    /** @return 玩家最大生命值 */
    public int getPlayerMaxHp() {
        return player.getMaxHealth();
    }

    /** @return 玩家每回合最大能量 */
    public int getPlayerMaxEnergy() {
        return player.getMaxEnergy();
    }

    /** @return 怪物最大生命值 */
    public int getMonsterMaxHp() {
        return enemy.getMaxHealth();
    }

    /** @return 当前回合数 */
    public int getTurnNumber() {
        return turnNumber;
    }

    /** @return 当前抽牌堆剩余牌数 */
    public int getDrawPileSize() {
        return piles.getDrawPileSize();
    }

    /** @return 当前弃牌堆牌数 */
    public int getDiscardPileSize() {
        return piles.getDiscardPileSize();
    }

    /** @return 当前消耗堆牌数 */
    public int getExhaustPileSize() {
        return piles.getExhaustPileSize();
    }

    /**
     * 判断当前是否是玩家可操作状态。
     *
     * @return 仅在玩家回合且战斗未结束时为 true
     */
    public boolean isPlayerTurn() {
        return playerTurn && !finished;
    }

    /** @return 战斗是否已经结束 */
    public boolean isFinished() {
        return finished;
    }

    /** @return 战斗结束时的展示文本，未结束时可能为空字符串 */
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

    /**
     * 获取当前手牌。
     *
     * @return 手牌中的牌实例列表，顺序为界面展示顺序
     */
    public List<CardInstance> getHand() {
        return piles.getHand();
    }

    /**
     * 抽牌堆快照，仅供调试界面查看。下一张在列表末尾。
     *
     * <p>{@code .stream()} 把列表转换为流；{@code .map(CardInstance::card)}
     * 把每个 {@code CardInstance} 转成它对应的 {@code Card}；
     * {@code .toList()} 把流重新收集成列表。</p>
     *
     * <p>{@code CardInstance::card} 是方法引用，等价于
     * {@code instance -> instance.card()}。</p>
     *
     * @return 仅包含卡牌模板的快照
     */
    public List<Card> getDrawPile() {
        return piles.getDrawPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /**
     * 弃牌堆快照，仅供调试界面查看。最近弃入的在列表末尾。
     *
     * @return 仅包含卡牌模板的快照
     */
    public List<Card> getDiscardPile() {
        return piles.getDiscardPile().stream()
                .map(CardInstance::card)
                .toList();
    }

    /**
     * 界面展示怪物下一动，方便看清攻防循环。
     *
     * @return 类似 "下回合：攻击 10" 或 "下回合：防御 +10"
     */
    public String getMonsterIntent() {
        if (finished) {
            return "已倒下";
        }
        MonsterAction nextAction = nextMonsterAction();
        return switch (nextAction.type()) {
            case ATTACK -> "下回合：攻击 " + nextAction.value();
            case DEFEND -> "下回合：防御 +" + nextAction.value();
        };
    }

    /**
     * 结构化怪物意图，供 HTTP 层序列化为 JSON。
     *
     * @return 怪物下一行动；战斗结束后为 null
     */
    public Intent getMonsterIntentInfo() {
        if (finished) {
            return null;
        }
        MonsterAction nextAction = nextMonsterAction();
        return new Intent(nextAction.type().name(), nextAction.value());
    }

    /**
     * 取走并清空本次操作产生的增量日志。
     *
     * <p>先复制一份快照，再清空原列表，最后用 {@code List.copyOf} 返回不可变副本，
     * 避免调用方后续修改列表影响战斗内部状态。</p>
     *
     * @return 从上次取走到现在新增的日志
     */
    public List<String> drainNewLogs() {
        List<String> snapshot = new ArrayList<>(newLogs);
        newLogs.clear();
        return List.copyOf(snapshot);
    }

    /**
     * 点击手牌时调用。只能在玩家回合打出。
     *
     * @param handIndex 手牌索引，从 0 开始
     * @return 出牌结果
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
     *
     * @param cardInstanceId 手牌实例的 id
     * @return 出牌结果
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

    /**
     * 执行实际出牌流程。
     *
     * <p>流程为：校验可打出 -> 校验能量 -> 从手牌移除 -> 执行卡牌效果
     * -> 放入弃牌堆或消耗堆 -> 检查战斗是否结束。</p>
     */
    private PlayCardResult playCardInternal(int handIndex) {
        CardInstance instance = piles.peekHand(handIndex);
        Card card = instance.card();
        // 某些未来卡牌可能是不可主动打出的状态牌或诅咒牌。
        if (!card.playable()) {
            log("「" + card.name() + "」无法打出。");
            return PlayCardResult.CARD_NOT_PLAYABLE;
        }

        // 能量不足时不消耗任何资源，也不移动手牌。
        if (!tryConsumeEnergy(card.cost())) {
            log("能量不足，无法打出「" + card.name() + "」。");
            return PlayCardResult.NOT_ENOUGH_ENERGY;
        }

        piles.removeFromHand(handIndex);
        log("玩家打出「" + card.name() + "」，消耗 " + card.cost() + " 点能量。");
        // 卡牌效果只通过 CardEffectContext 操作战斗，不能直接访问 Combat 内部字段。
        card.effect().apply(new CombatCardEffectContext());

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
     *
     * <p>该方法把一次完整回合边界集中处理，避免界面层分别调用多个私有方法。</p>
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

    /**
     * 初始化一场新战斗。
     *
     * <p>会重置玩家本场临时资源、怪物行动索引、回合数、结果和日志，并重新填充牌堆。
     * 玩家生命值由 Player 实体跨关卡保留，这里不会回满。</p>
     */
    private void startNewFight() {
        player.resetForBattle();
        enemy.clearArmor();
        enemy.clearStatuses();
        monsterActionIndex = 0;
        finished = false;
        resultText = "";
        resultCode = null;
        turnNumber = 1;
        newLogs.clear();
        piles.initializeInstances(battleDeck);

        log("战斗开始。玩家 HP " + player.getHealth()
                + "，怪物 HP " + enemy.getHealth() + "。");
        beginPlayerTurn();
    }

    /**
     * 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。
     *
     * <p>先清护甲再抽牌，是因为本作中玩家护甲只在怪物回合生效，下一回合重新计算。</p>
     */
    private void beginPlayerTurn() {
        playerTurn = true;
        player.clearArmor();
        player.refresh();
        drawToHandSize();
        log("—— 玩家回合 —— 能量 " + player.getEnergy()
                + "，抽牌 " + piles.getHandSize() + " 张。");
    }

    /**
     * 执行怪物回合。
     *
     * <p>怪物按固定模式交替行动：第一回合攻击，第二回合叠护盾，
     * 之后再回到攻击。每次行动前会先清空怪物自己的护盾。</p>
     */
    private void runMonsterTurn() {
        playerTurn = false;
        // 怪物回合开始时清空自己剩余护盾，再按行动脚本执行本回合动作。
        enemy.clearArmor();

        MonsterAction action = nextMonsterAction();
        switch (action.type()) {
            case ATTACK -> {
                int damage = enemy.calcDealtDamage(action.value());
                int dealt = player.receiveDamage(damage);
                log("怪物攻击，对玩家造成 " + dealt + " 点伤害。");
            }
            case DEFEND -> {
                enemy.addArmor(action.value());
                log("怪物防御，获得 " + action.value() + " 点护盾。");
            }
        }

        monsterActionIndex = (monsterActionIndex + 1) % monsterActions.size();
        checkFinished();
    }

    /** 把手牌补到 {@link #HAND_SIZE} 张。 */
    private void drawToHandSize() {
        piles.drawToHandSize(HAND_SIZE);
    }

    /**
     * 返回怪物当前要执行的行动。
     *
     * <p>当前只读取，不推进索引；真正推进在 runMonsterTurn() 末尾完成。</p>
     */
    private MonsterAction nextMonsterAction() {
        return monsterActions.get(monsterActionIndex);
    }

    /**
     * 尝试消费能量。
     *
     * @param cost 需要消耗的能量
     * @return 成功扣除返回 true；费用非法或能量不足返回 false
     */
    private boolean tryConsumeEnergy(int cost) {
        return player.consume(cost);
    }

    /**
     * 每次生命值变化后检查胜负。
     *
     * <p>先判断怪物是否死亡，再判断玩家是否死亡。战斗结束后不再重复写结果。</p>
     */
    private void checkFinished() {
        if (finished) {
            return;
        }
        if (enemy.isDead()) {
            finished = true;
            playerTurn = false;
            resultText = "胜利：怪物血量已归零。";
            resultCode = "VICTORY";
            log(resultText);
        } else if (player.isDead()) {
            finished = true;
            playerTurn = false;
            resultText = "失败：玩家血量已归零。";
            resultCode = "DEFEAT";
            log(resultText);
        }
    }

    /**
     * 把旧版 Card 模板列表转换成带唯一实例编号的 CardInstance 列表。
     *
     * <p>这只用于旧的 Combat(Consumer) 测试构造方式。正式流程中，
     * RunState 已经持有 CardInstance，不需要重新生成编号。</p>
     */
    private static List<CardInstance> createInstances(List<Card> cards) {
        List<CardInstance> instances = new ArrayList<>();
        for (Card card : cards) {
            instances.add(new CardInstance(UUID.randomUUID().toString(), card));
        }
        return List.copyOf(instances);
    }

    /**
     * 同时把日志发给外部 logger，并保存到内部增量日志列表。
     *
     * @param line 日志文本
     */
    private void log(String line) {
        logger.accept(line);
        newLogs.add(line);
    }

    /**
     * 怪物行动类型。
     *
     * <p>当前 MVP 只支持攻击和防御。以后可以继续增加施加状态、回血等动作。</p>
     */
    public enum MonsterActionType {
        /** 攻击玩家。 */
        ATTACK,
        /** 给自己增加护甲。 */
        DEFEND
    }

    /**
     * 怪物脚本中的单个行动。
     *
     * @param type 行动类型
     * @param value 攻击伤害或护甲数值
     */
    public record MonsterAction(MonsterActionType type, int value) {
        public MonsterAction {
            Objects.requireNonNull(type, "怪物行动类型不能为 null");
            if (value < 0) {
                throw new IllegalArgumentException("怪物行动数值不能为负数");
            }
        }
    }

    /**
     * 一场战斗的敌人行动配置。
     *
     * @param actions 按顺序循环执行的行动列表
     */
    public record EncounterDefinition(List<MonsterAction> actions) {
        public EncounterDefinition {
            Objects.requireNonNull(actions, "敌人行动列表不能为 null");
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("敌人行动列表不能为空");
            }
            actions = List.copyOf(actions);
        }

        /**
         * 创建旧版战斗使用的交替攻击/防御脚本。
         *
         * @param attack 攻击伤害
         * @param block 防御叠甲
         * @return 包含一次攻击和一次防御的配置
         */
        public static EncounterDefinition alternating(int attack, int block) {
            return new EncounterDefinition(List.of(
                    new MonsterAction(MonsterActionType.ATTACK, attack),
                    new MonsterAction(MonsterActionType.DEFEND, block)));
        }
    }

    /**
     * 怪物下一回合意图。
     *
     * <p>这是一个嵌套在 {@code Combat} 内的 record，用于把“行动类型”和“数值”
     * 打包成一个不可变对象。record 会自动提供 {@code type()} 和 {@code value()}。</p>
     */
    public record Intent(String type, int value) {
    }

    /**
     * 出牌结果。HTTP 层可以直接根据此枚举映射错误码。
     *
     * <p>枚举比返回整数或魔法字符串更安全：调用方看到明确的有限取值，
     * 编译器也能在 switch 中帮助检查遗漏。</p>
     */
    public enum PlayCardResult {
        /** 出牌成功。 */
        SUCCESS,
        /** 当前不是玩家回合。 */
        NOT_PLAYER_TURN,
        /** 手牌索引或牌实例 id 无效。 */
        INVALID_CARD,
        /** 能量不足。 */
        NOT_ENOUGH_ENERGY,
        /** 这张牌不可主动打出。 */
        CARD_NOT_PLAYABLE,
        /** 战斗已经结束。 */
        BATTLE_FINISHED
    }

    /**
     * 把 Combat 当前操作暴露给卡牌效果，隔离卡牌层与未来的实体层。
     *
     * <p>这是非静态内部类，实例隐含持有外部 {@code Combat} 对象引用，
     * 因此可以直接访问玩家、怪物、能量等字段，也可以通过
     * {@code Combat.this.log(...)} 调用外部方法。</p>
     */
    private final class CombatCardEffectContext implements CardEffectContext {

        @Override
        public void dealDamageToMonster(int amount) {
            // normalizeAmount 先把负数伤害修正为 0。
            int baseDamage = normalizeAmount(amount);
            int calculatedDamage = player.calcDealtDamage(baseDamage);
            int dealt = enemy.receiveDamage(calculatedDamage);
            log("对怪物造成 " + dealt + " 点伤害。");
        }

        @Override
        public void addMonsterBlock(int amount) {
            // 非正数护甲不产生任何效果，也不写日志。
            if (amount <= 0) {
                return;
            }
            enemy.addArmor(amount);
            log("怪物获得 " + amount + " 点护甲。");
        }

        @Override
        public void dealDamageToPlayer(int amount) {
            // 这个入口目前服务于「失去生命」类卡牌，因此直接扣血，
            // 不经过护甲吸收和易伤结算。怪物攻击玩家走 Combat.runMonsterTurn()。
            int dealt = player.takeDamage(normalizeAmount(amount));
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
            // 治疗不能超过玩家最大生命值。
            player.heal(amount);
            log("玩家恢复 " + (player.getHealth() - before) + " 点生命。");
        }

        @Override
        public void drawCards(int count) {
            // CardPiles.draw 会返回实际抽到的牌；这里只关心数量。
            int drawn = piles.draw(count).size();
            log("额外抽 " + drawn + " 张牌。");
        }

        @Override
        public void addPlayerEnergy(int amount) {
            if (amount <= 0) {
                return;
            }
            int before = player.getEnergy();
            // 能量也不能超过本回合上限。
            player.addEnergy(amount);
            log("玩家获得 " + (player.getEnergy() - before) + " 点能量。");
        }

        @Override
        public void log(String line) {
            // 明确使用外部 Combat 实例的 log，避免与内部类自身的命名混淆。
            Combat.this.log(line);
        }

        /**
         * 把传入伤害或护甲修正为不小于 0 的整数。
         *
         * @param amount 原始数值
         * @return 修正后的数值
         */
        private int normalizeAmount(int amount) {
            return Math.max(0, amount);
        }
    }
}
