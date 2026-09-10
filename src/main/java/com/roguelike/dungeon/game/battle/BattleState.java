package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.Enemy;
import com.roguelike.dungeon.game.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * 一场战斗的可变状态容器。
 *
 * <p>只保存玩家、怪物、牌堆和回合标记，不依赖 {@link Combat}，
 * 也不包含出牌、怪物 AI 或胜负通知逻辑。</p>
 */
public final class BattleState {

    private final Player player;
    private final Enemy monster;
    private final CardPiles piles;
    private final List<CardInstance> battleDeck;

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
        this.monster = new Enemy(monsterMaxHp);
    }

    public Player getPlayer() {
        return player;
    }

    /** 本场战斗的怪物实体（承载血量 / 护甲 / 状态效果）。 */
    public Enemy getMonster() {
        return monster;
    }

    public CardPiles getPiles() {
        return piles;
    }

    public List<CardInstance> getBattleDeck() {
        return battleDeck;
    }

    public int getMonsterMaxHp() {
        return monster.getMaxHealth();
    }

    public int getMonsterHp() {
        return monster.getHealth();
    }

    public void setMonsterHp(int monsterHp) {
        monster.setHealth(monsterHp);
    }

    public int getMonsterBlock() {
        return monster.getArmor();
    }

    public void setMonsterBlock(int monsterBlock) {
        monster.clearArmor();
        monster.addArmor(monsterBlock);
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
     * 结算一次伤害，攻击方的「虚弱」与目标的「易伤」都参与结算。
     *
     * @param toMonster true 表示伤害打向怪物，false 表示打向玩家
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    public int applyDamage(boolean toMonster, int amount) {
        if (toMonster) {
            return monster.receiveDamage(player.calcDealtDamage(amount));
        }
        return player.receiveDamage(monster.calcDealtDamage(amount));
    }

    /**
     * 给怪物增加护甲。amount &lt;= 0 时忽略。
     */
    public void addMonsterBlock(int amount) {
        monster.addArmor(amount);
    }
}
