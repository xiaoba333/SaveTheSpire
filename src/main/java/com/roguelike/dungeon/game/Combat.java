package com.roguelike.dungeon.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 杀戮尖塔风格的最小战斗规则：抽牌、出牌、结束回合、怪物攻防交替、护盾抵伤。
 */
public class Combat {

    public static final int PLAYER_MAX_HP = 50;
    public static final int MONSTER_MAX_HP = 30;
    public static final int MONSTER_ATTACK = 10;
    public static final int MONSTER_BLOCK = 10;
    public static final int HAND_SIZE = 5;
    public static final int DECK_ATTACK_COUNT = 5;
    public static final int DECK_DEFEND_COUNT = 5;

    private final Consumer<String> logger;

    private int playerHp;
    private int playerBlock;
    private int monsterHp;
    private int monsterBlock;
    /** true 表示怪物下一次行动是攻击，false 表示给自己叠护盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText;

    private final List<Card> deck = new ArrayList<>();
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> discard = new ArrayList<>();

    public Combat(Consumer<String> logger) {
        this.logger = logger;
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
        return Collections.unmodifiableList(hand);
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
        if (!isPlayerTurn() || handIndex < 0 || handIndex >= hand.size()) {
            return;
        }

        Card card = hand.remove(handIndex);
        discard.add(card);

        if (card.type() == CardType.ATTACK) {
            int dealt = applyDamage(true, card.type().value());
            log("玩家打出「" + card.label() + "」，对怪物造成 " + dealt + " 点伤害。");
        } else {
            playerBlock += card.type().value();
            log("玩家打出「" + card.label() + "」，获得 " + card.type().value() + " 点护盾（当前护盾 " + playerBlock + "）。");
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

        discardHand();
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
        monsterWillAttack = true;
        finished = false;
        resultText = "";
        deck.clear();
        hand.clear();
        discard.clear();

        for (int i = 0; i < DECK_ATTACK_COUNT; i++) {
            deck.add(new Card(CardType.ATTACK));
        }
        for (int i = 0; i < DECK_DEFEND_COUNT; i++) {
            deck.add(new Card(CardType.DEFEND));
        }
        Collections.shuffle(deck);

        log("战斗开始。玩家 HP " + playerHp + "，怪物 HP " + monsterHp + "。");
        beginPlayerTurn();
    }

    /** 玩家回合开始：清空自身未消耗护盾（参考杀戮尖塔），再抽满手牌。 */
    private void beginPlayerTurn() {
        playerTurn = true;
        playerBlock = 0;
        drawToHandSize();
        log("—— 玩家回合 —— 抽牌 " + hand.size() + " 张。点击卡牌打出，或结束回合。");
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
        while (hand.size() < HAND_SIZE) {
            if (deck.isEmpty()) {
                if (discard.isEmpty()) {
                    break;
                }
                deck.addAll(discard);
                discard.clear();
                Collections.shuffle(deck);
                log("抽牌堆用尽，弃牌堆洗回抽牌堆。");
            }
            hand.add(deck.remove(deck.size() - 1));
        }
    }

    private void discardHand() {
        discard.addAll(hand);
        hand.clear();
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
}
