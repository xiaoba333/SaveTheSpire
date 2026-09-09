package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;

import java.util.ArrayList;
import java.util.List;
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

    // 以下常量是当前战斗的平衡参数。它们都是 public，方便界面或测试统一读取。
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

    /** 牌堆管理器：负责抽牌堆、手牌、弃牌堆、消耗堆的移动。 */
    private final CardPiles piles;

    // 玩家和怪物的当前战斗状态。
    private int playerHp;
    private int playerBlock;
    private int monsterHp;
    private int monsterBlock;
    private int energy;
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
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
        this.logger = logger;
        this.piles = new CardPiles(logger);
        startNewFight();
    }

    /** @return 玩家当前生命值 */
    public int getPlayerHp() {
        return playerHp;
    }

    /** @return 玩家当前护甲值 */
    public int getPlayerBlock() {
        return playerBlock;
    }

    /** @return 怪物当前生命值 */
    public int getMonsterHp() {
        return monsterHp;
    }

    /** @return 怪物当前护甲值 */
    public int getMonsterBlock() {
        return monsterBlock;
    }

    /** @return 玩家当前回合剩余能量 */
    public int getEnergy() {
        return energy;
    }

    /** @return 玩家最大生命值 */
    public int getPlayerMaxHp() {
        return PLAYER_MAX_HP;
    }

    /** @return 玩家每回合最大能量 */
    public int getPlayerMaxEnergy() {
        return PLAYER_MAX_ENERGY;
    }

    /** @return 怪物最大生命值 */
    public int getMonsterMaxHp() {
        return MONSTER_MAX_HP;
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
        return monsterWillAttack ? "下回合：攻击 " + MONSTER_ATTACK : "下回合：防御 +" + MONSTER_BLOCK;
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
        return monsterWillAttack
                ? new Intent("ATTACK", MONSTER_ATTACK)
                : new Intent("DEFEND", MONSTER_BLOCK);
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
     * <p>会重置双方血量、护甲、能量、回合数、结果和日志，并重新填充牌堆。</p>
     */
    private void startNewFight() {
        playerHp = PLAYER_MAX_HP;
        playerBlock = 0;
        monsterHp = MONSTER_MAX_HP;
        monsterBlock = 0;
        energy = 0;
        monsterWillAttack = true;
        finished = false;
        resultText = "";
        resultCode = null;
        turnNumber = 1;
        newLogs.clear();
        piles.initialize(CardLibrary.startingDeck());

        log("战斗开始。玩家 HP " + playerHp + "，怪物 HP " + monsterHp + "。");
        beginPlayerTurn();
    }

    /**
     * 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。
     *
     * <p>先清护甲再抽牌，是因为本作中玩家护甲只在怪物回合生效，下一回合重新计算。</p>
     */
    private void beginPlayerTurn() {
        playerTurn = true;
        playerBlock = 0;
        energy = PLAYER_MAX_ENERGY;
        drawToHandSize();
        log("—— 玩家回合 —— 能量 " + energy + "，抽牌 " + piles.getHandSize() + " 张。");
    }

    /**
     * 执行怪物回合。
     *
     * <p>怪物按固定模式交替行动：第一回合攻击，第二回合叠护盾，
     * 之后再回到攻击。每次行动前会先清空怪物自己的护盾。</p>
     */
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
     *
     * <p>伤害公式为：先用护甲吸收一部分，剩余部分扣生命。生命最低扣到 0，
     * 不会变成负数。</p>
     */
    private int applyDamage(boolean toMonster, int amount) {
        if (toMonster) {
            int absorbed = Math.min(monsterBlock, amount);
            monsterBlock -= absorbed;
            int hpLoss = amount - absorbed;
            monsterHp = Math.max(0, monsterHp - hpLoss);
            return hpLoss;
        }
        int absorbed = Math.min(playerBlock, amount);
        playerBlock -= absorbed;
        int hpLoss = amount - absorbed;
        playerHp = Math.max(0, playerHp - hpLoss);
        return hpLoss;
    }

    /** 把手牌补到 {@link #HAND_SIZE} 张。 */
    private void drawToHandSize() {
        piles.drawToHandSize(HAND_SIZE);
    }

    /**
     * 尝试消费能量。
     *
     * @param cost 需要消耗的能量
     * @return 成功扣除返回 true；费用非法或能量不足返回 false
     */
    private boolean tryConsumeEnergy(int cost) {
        if (cost < 0 || energy < cost) {
            return false;
        }
        energy -= cost;
        return true;
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
        if (monsterHp <= 0) {
            finished = true;
            playerTurn = false;
            resultText = "胜利：怪物血量已归零。";
            resultCode = "VICTORY";
            log(resultText);
        } else if (playerHp <= 0) {
            finished = true;
            playerTurn = false;
            resultText = "失败：玩家血量已归零。";
            resultCode = "DEFEAT";
            log(resultText);
        }
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
            int dealt = applyDamage(true, normalizeAmount(amount));
            log("对怪物造成 " + dealt + " 点伤害。");
        }

        @Override
        public void addMonsterBlock(int amount) {
            // 非正数护甲不产生任何效果，也不写日志。
            if (amount <= 0) {
                return;
            }
            monsterBlock += amount;
            log("怪物获得 " + amount + " 点护甲。");
        }

        @Override
        public void dealDamageToPlayer(int amount) {
            int dealt = applyDamage(false, normalizeAmount(amount));
            log("玩家受到 " + dealt + " 点伤害。");
        }

        @Override
        public void addPlayerBlock(int amount) {
            if (amount <= 0) {
                return;
            }
            playerBlock += amount;
            log("玩家获得 " + amount + " 点护甲。");
        }

        @Override
        public void healPlayer(int amount) {
            if (amount <= 0) {
                return;
            }
            int before = playerHp;
            // 治疗不能超过玩家最大生命值。
            playerHp = Math.min(PLAYER_MAX_HP, playerHp + amount);
            log("玩家恢复 " + (playerHp - before) + " 点生命。");
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
            int before = energy;
            // 能量也不能超过本回合上限。
            energy = Math.min(PLAYER_MAX_ENERGY, energy + amount);
            log("玩家获得 " + (energy - before) + " 点能量。");
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
