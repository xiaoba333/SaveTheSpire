package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardType;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.BattleInfo;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.entity.StatusEffect;
import com.roguelike.dungeon.game.relic.RelicService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一场战斗的可变状态容器。
 *
 * <p>只保存玩家、怪物、牌堆和回合标记，不依赖 {@link Combat}，
 * 也不包含出牌、怪物 AI 或胜负通知逻辑。</p>
 *
 * <p>同时实现 {@link BattleInfo}，作为遗物可读写的战斗视图。
 * 遗物服务由 {@link Combat} 在装配时注入；独立 Demo 或单元测试下可以为 null，
 * 此时所有遗物钩子自动跳过，行为与引入遗物之前完全一致。</p>
 */
public final class BattleState implements BattleInfo {

    private final Player player;
    private final CardPiles piles;
    private final List<CardInstance> battleDeck;
    private final int monsterMaxHp;

    private int monsterHp;
    private int monsterBlock;
    /** 怪物身上的状态（易伤 / 虚弱 / 中毒）。玩家有独立的状态存储。 */
    private final Map<StatusEffect, Integer> monsterStatuses =
            new EnumMap<>(StatusEffect.class);
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText = "";
    private String resultCode;
    private int turnNumber = 1;

    /** 本回合已打出的牌数，供「记账本」这类遗物判断节奏。 */
    private int cardsPlayedThisTurn;
    /** 本场战斗累计打出的牌数。 */
    private int cardsPlayedThisBattle;
    /** 本场战斗累计打出的攻击牌数，供苦无 / 手里剑这类计数遗物使用。 */
    private int attacksPlayedThisBattle;
    /** 本场战斗累计打出的技能牌数。 */
    private int skillsPlayedThisBattle;

    /** 遗物分发器，由 Combat 注入；为 null 时所有遗物钩子跳过。 */
    private RelicService relicService;

    /**
     * @param player 本局共享玩家（生命会保留到后续关卡）
     * @param battleDeck 本场战斗使用的牌组快照
     * @param piles 本场战斗的四类牌堆
     * @param monsterMaxHp 本场怪物生命上限
     */
    public BattleState(
            Player player,
            List<CardInstance> battleDeck,
            CardPiles piles,
            int monsterMaxHp) {
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        this.battleDeck = List.copyOf(Objects.requireNonNull(
                battleDeck, "战斗牌组不能为 null"));
        this.piles = Objects.requireNonNull(piles, "牌堆不能为 null");
        this.monsterMaxHp = monsterMaxHp;
        this.monsterHp = monsterMaxHp;
    }

    public Player getPlayer() {
        return player;
    }

    public CardPiles getPiles() {
        return piles;
    }

    public List<CardInstance> getBattleDeck() {
        return battleDeck;
    }

    public int getMonsterMaxHp() {
        return monsterMaxHp;
    }

    public int getMonsterHp() {
        return monsterHp;
    }

    public void setMonsterHp(int monsterHp) {
        this.monsterHp = monsterHp;
    }

    public int getMonsterBlock() {
        return monsterBlock;
    }

    public void setMonsterBlock(int monsterBlock) {
        this.monsterBlock = monsterBlock;
    }

    public boolean isMonsterWillAttack() {
        return monsterWillAttack;
    }

    public void setMonsterWillAttack(boolean monsterWillAttack) {
        this.monsterWillAttack = monsterWillAttack;
    }

    public boolean isPlayerTurn() {
        return playerTurn;
    }

    public void setPlayerTurn(boolean playerTurn) {
        this.playerTurn = playerTurn;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    // ---------- 遗物接入 ----------

    /** 注入遗物分发器。由 {@link Combat} 在装配战斗时调用。 */
    public void setRelicService(RelicService relicService) {
        this.relicService = relicService;
    }

    /** 分发一次遗物触发；未接入遗物时返回原值。 */
    private int fireRelic(RelicTrigger trigger, int value) {
        if (relicService == null) {
            return value;
        }
        return relicService.fire(trigger, value);
    }

    /** 战斗开始时清空怪物身上的全部状态。 */
    public void clearMonsterStatuses() {
        monsterStatuses.clear();
    }

    /** 玩家回合开始时清空本回合计数。 */
    public void resetTurnCounters() {
        cardsPlayedThisTurn = 0;
    }

    /** 战斗开始时清空本场累计计数。 */
    public void resetBattleCounters() {
        cardsPlayedThisTurn = 0;
        cardsPlayedThisBattle = 0;
        attacksPlayedThisBattle = 0;
        skillsPlayedThisBattle = 0;
    }

    /**
     * 记录一次成功出牌，同时累加本回合与本场计数。
     *
     * @param type 打出的卡牌类型
     */
    public void onCardPlayed(CardType type) {
        cardsPlayedThisTurn++;
        cardsPlayedThisBattle++;
        if (type == CardType.ATTACK) {
            attacksPlayedThisBattle++;
        } else if (type == CardType.SKILL) {
            skillsPlayedThisBattle++;
        }
    }

    // ---------- BattleInfo：遗物可读的战斗视图 ----------

    @Override
    public int monsterHp() {
        return monsterHp;
    }

    @Override
    public int monsterMaxHp() {
        return monsterMaxHp;
    }

    @Override
    public int monsterBlock() {
        return monsterBlock;
    }

    @Override
    public boolean monsterWillAttack() {
        return monsterWillAttack;
    }

    @Override
    public int turnNumber() {
        return turnNumber;
    }

    @Override
    public int cardsPlayedThisTurn() {
        return cardsPlayedThisTurn;
    }

    @Override
    public int cardsPlayedThisBattle() {
        return cardsPlayedThisBattle;
    }

    @Override
    public int attacksPlayedThisBattle() {
        return attacksPlayedThisBattle;
    }

    @Override
    public int skillsPlayedThisBattle() {
        return skillsPlayedThisBattle;
    }

    /** 怪物身上某种状态的层数。 */
    public int getMonsterStacks(StatusEffect effect) {
        return monsterStacks(effect);
    }

    @Override
    public int monsterStacks(StatusEffect effect) {
        if (effect == null) {
            return 0;
        }
        return monsterStatuses.getOrDefault(effect, 0);
    }

    /** 给怪物叠加状态层数，层数不会低于 0，减到 0 时移除。 */
    @Override
    public void addMonsterStacks(StatusEffect effect, int amount) {
        if (effect == null || amount == 0) {
            return;
        }
        int next = Math.max(0, monsterStacks(effect) + amount);
        if (next == 0) {
            monsterStatuses.remove(effect);
        } else {
            monsterStatuses.put(effect, next);
        }
    }

    @Override
    public int dealDirectDamageToMonster(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = monsterHp;
        monsterHp = Math.max(0, monsterHp - amount);
        return before - monsterHp;
    }

    /**
     * 结算一次伤害。
     *
     * <p>结算顺序：遗物修正 → 目标易伤 → 护甲吸收 → 扣血。
     * 遗物看到的是易伤结算之前的原始伤害。</p>
     *
     * @param toMonster true 表示伤害打向怪物，false 表示打向玩家
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    public int applyDamage(boolean toMonster, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (toMonster) {
            int modified = fireRelic(RelicTrigger.DAMAGE_DEALT, amount);
            if (modified <= 0) {
                return 0;
            }
            if (monsterStacks(StatusEffect.VULNERABLE) > 0) {
                modified = modified * 3 / 2;
            }
            int absorbed = Math.min(monsterBlock, modified);
            monsterBlock -= absorbed;
            int hpLoss = modified - absorbed;
            monsterHp = Math.max(0, monsterHp - hpLoss);
            return hpLoss;
        }

        int incoming = fireRelic(RelicTrigger.DAMAGE_TAKEN, amount);
        if (incoming <= 0) {
            return 0;
        }
        return player.receiveDamage(incoming);
    }

    /**
     * 给怪物增加护甲。amount &lt;= 0 时忽略。
     */
    public void addMonsterBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        monsterBlock += amount;
    }

    /** 给怪物叠加指定状态的层数。 */
    public void addMonsterStatus(StatusEffect effect, int amount) {
        if (effect == null) {
            return;
        }
        int next = Math.max(0, monsterStatuses.getOrDefault(effect, 0) + amount);
        if (next == 0) {
            monsterStatuses.remove(effect);
        } else {
            monsterStatuses.put(effect, next);
        }
    }

    /** 获取怪物指定状态的当前层数。 */
    public int getMonsterStatusStacks(StatusEffect effect) {
        return monsterStatuses.getOrDefault(effect, 0);
    }
}

