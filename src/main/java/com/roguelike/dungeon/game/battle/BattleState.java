package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一场战斗的可变状态容器。
 *
 * <p>只保存玩家、怪物、牌堆和回合标记，不依赖 {@link Combat}，
 * 也不包含出牌、怪物 AI 或胜负通知逻辑。</p>
 */
public final class BattleState {

    private final Player player;
    private final CardPiles piles;
    private final List<CardInstance> battleDeck;
    private final int monsterMaxHp;

    private int monsterHp;
    private int monsterBlock;
    private final Map<StatusEffect, Integer> monsterStatuses =
            new EnumMap<>(StatusEffect.class);
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText = "";
    private String resultCode;
    private int turnNumber = 1;

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

    /**
     * 结算一次伤害。公式与原 {@code Combat.applyDamage} 完全一致。
     *
     * @param toMonster true 表示伤害打向怪物，false 表示打向玩家
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    public int applyDamage(boolean toMonster, int amount) {
        if (toMonster) {
            int absorbed = Math.min(monsterBlock, amount);
            monsterBlock -= absorbed;
            int hpLoss = amount - absorbed;
            monsterHp = Math.max(0, monsterHp - hpLoss);
            return hpLoss;
        }
        return player.receiveDamage(amount);
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
