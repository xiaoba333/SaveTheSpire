package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardEffectContext;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.deck.CardPiles;

import java.util.List;
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

    private int playerHp;
    private int playerBlock;
    private int monsterHp;
    private int monsterBlock;
    private int energy;
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText;

    public Combat(Consumer<String> logger) {
        this.logger = logger;
        this.piles = new CardPiles(logger);
        startNewFight();
    }

    public int getPlayerHp() {
        return playerHp;
    }

    public int getPlayerBlock() {
        return playerBlock;
    }

    public int getMonsterHp() {
        return monsterHp;
    }

    public int getMonsterBlock() {
        return monsterBlock;
    }

    public int getEnergy() {
        return energy;
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

    public List<Card> getHand() {
        return piles.getHand();
    }

    /** 界面展示怪物下一动，方便看清攻防循环。 */
    public String getMonsterIntent() {
        if (finished) {
            return "已倒下";
        }
        return monsterWillAttack ? "下回合：攻击 " + MONSTER_ATTACK : "下回合：防御 +" + MONSTER_BLOCK;
    }

    /**
     * 点击手牌时调用。只能在玩家回合打出。
     */
    public void playCard(int handIndex) {
        if (!isPlayerTurn() || handIndex < 0 || handIndex >= piles.getHandSize()) {
            return;
        }

        Card card = piles.peekHand(handIndex);
        if (!card.playable()) {
            log("「" + card.name() + "」无法打出。");
            return;
        }

        if (!tryConsumeEnergy(card.cost())) {
            log("能量不足，无法打出「" + card.name() + "」。");
            return;
        }

        piles.removeFromHand(handIndex);
        log("玩家打出「" + card.name() + "」，消耗 " + card.cost() + " 点能量。");
        card.effect().apply(new CombatCardEffectContext());

        if (card.exhausts()) {
            piles.sendToExhaust(card);
            log("「" + card.name() + "」已消耗。");
        } else {
            piles.sendToDiscard(card);
        }
        checkFinished();
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

        beginPlayerTurn();
    }

    private void startNewFight() {
        playerHp = PLAYER_MAX_HP;
        playerBlock = 0;
        monsterHp = MONSTER_MAX_HP;
        monsterBlock = 0;
        energy = 0;
        monsterWillAttack = true;
        finished = false;
        resultText = "";
        piles.initialize(CardLibrary.startingDeck());

        log("战斗开始。玩家 HP " + playerHp + "，怪物 HP " + monsterHp + "。");
        beginPlayerTurn();
    }

    /** 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。 */
    private void beginPlayerTurn() {
        playerTurn = true;
        playerBlock = 0;
        energy = PLAYER_MAX_ENERGY;
        drawToHandSize();
        log("—— 玩家回合 —— 能量 " + energy + "，抽牌 " + piles.getHandSize() + " 张。");
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

    private void drawToHandSize() {
        piles.drawToHandSize(HAND_SIZE);
    }

    private boolean tryConsumeEnergy(int cost) {
        if (cost < 0 || energy < cost) {
            return false;
        }
        energy -= cost;
        return true;
    }

    private void checkFinished() {
        if (finished) {
            return;
        }
        if (monsterHp <= 0) {
            finished = true;
            playerTurn = false;
            resultText = "胜利：怪物血量已归零。";
            log(resultText);
        } else if (playerHp <= 0) {
            finished = true;
            playerTurn = false;
            resultText = "失败：玩家血量已归零。";
            log(resultText);
        }
    }

    private void log(String line) {
        logger.accept(line);
    }

    /**
     * 把 Combat 当前操作暴露给卡牌效果，隔离卡牌层与未来的实体层。
     */
    private final class CombatCardEffectContext implements CardEffectContext {

        @Override
        public void dealDamageToMonster(int amount) {
            int dealt = applyDamage(true, normalizeAmount(amount));
            log("对怪物造成 " + dealt + " 点伤害。");
        }

        @Override
        public void addMonsterBlock(int amount) {
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
            playerHp = Math.min(PLAYER_MAX_HP, playerHp + amount);
            log("玩家恢复 " + (playerHp - before) + " 点生命。");
        }

        @Override
        public void drawCards(int count) {
            int drawn = piles.draw(count).size();
            log("额外抽 " + drawn + " 张牌。");
        }

        @Override
        public void addPlayerEnergy(int amount) {
            if (amount <= 0) {
                return;
            }
            int before = energy;
            energy = Math.min(PLAYER_MAX_ENERGY, energy + amount);
            log("玩家获得 " + (energy - before) + " 点能量。");
        }

        @Override
        public void log(String line) {
            Combat.this.log(line);
        }

        private int normalizeAmount(int amount) {
            return Math.max(0, amount);
        }
    }
}
