package com.roguelike.dungeon.debug;

import com.roguelike.dungeon.flow.GameController;
import com.roguelike.dungeon.flow.GamePhase;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapTextRenderer;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.run.RunState;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * 可在 IntelliJ 控制台中运行的纯文字游戏流程。
 *
 * <p>这只是调试入口，不参与核心规则，未来 Unity 可以直接调用
 * 同一个 {@link GameController}。</p>
 */
public final class GameFlowDebugMain {
    private static final int DEFAULT_ACT_COUNT = 1;
    private static final List<Card> REWARD_POOL = List.of(
            CardLibrary.BASH,
            CardLibrary.QUICK_SLASH,
            CardLibrary.HEAVY_STRIKE,
            CardLibrary.IRON_WAVE,
            CardLibrary.SHRUG_IT_OFF,
            CardLibrary.BLOODLETTING);

    private final Scanner scanner;
    private final GameController controller;
    private final MapTextRenderer mapRenderer = new MapTextRenderer();
    private boolean running = true;

    private GameFlowDebugMain(Scanner scanner, GameController controller) {
        this.scanner = scanner;
        this.controller = controller;
    }

    public static void main(String[] args) {
        try {
            long seed = readSeed(args);
            int actCount = readActCount(args);
            RunState runState = new RunState(
                    new Player(Combat.PLAYER_MAX_HP, Combat.PLAYER_MAX_ENERGY),
                    createStartingDeck(),
                    0,
                    seed,
                    actCount);
            GameController controller = new GameController(
                    runState,
                    REWARD_POOL,
                    line -> System.out.println("[战斗] " + line));

            System.out.println("=== 杀戮尖塔文字流程 MVP ===");
            System.out.println("地图种子：" + seed);
            System.out.println("章节数量：" + actCount);
            System.out.println("任何阶段输入 quit 可以退出。\n");

            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                new GameFlowDebugMain(scanner, controller).run();
            }
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
            printUsage();
        }
    }

    private void run() {
        while (running) {
            switch (controller.getPhase()) {
                case MAP -> handleMap();
                case BATTLE -> handleBattle();
                case REWARD -> handleReward();
                case EVENT, SHOP, REST -> handlePlaceholderLevel();
                case VICTORY -> {
                    printRunSummary("恭喜通关！");
                    running = false;
                }
                case DEFEAT -> {
                    printRunSummary("游戏失败，玩家已倒下。");
                    running = false;
                }
            }
        }
    }

    private void handleMap() {
        RunState state = controller.getRunState();
        System.out.println("\n=== 第 " + state.getCurrentAct() + " / "
                + state.getTotalActs() + " 章 ===");
        System.out.println("玩家 HP：" + state.getPlayer().getHealth() + " / "
                + state.getPlayer().getMaxHealth()
                + "    金币：" + state.getGold()
                + "    永久牌组：" + state.getDeck().size() + " 张");
        System.out.print(mapRenderer.render(controller.getMapService()));
        System.out.println("可选节点：");
        for (MapNode node : controller.getMapService().getAvailableNodes()) {
            System.out.println("  " + node.id() + " - " + typeName(node));
        }

        String input = readLine("请输入节点编号：");
        if (!running) {
            return;
        }
        try {
            int nodeId = Integer.parseInt(input);
            controller.selectNode(nodeId);
        } catch (NumberFormatException exception) {
            System.out.println("请输入整数节点编号。");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("无法进入节点：" + exception.getMessage());
        }
    }

    private void handleBattle() {
        Combat combat = controller.getCurrentCombat().orElseThrow();
        while (running && controller.getPhase() == GamePhase.BATTLE) {
            printBattleState(combat);
            String input = readLine("输入 play <手牌编号>、end 或 help：");
            if (!running) {
                return;
            }

            if (input.equalsIgnoreCase("end")) {
                combat.endPlayerTurn();
            } else if (input.equalsIgnoreCase("help")) {
                printBattleHelp();
            } else if (input.toLowerCase().startsWith("play ")) {
                playCard(combat, input.substring(5).trim());
            } else {
                System.out.println("未知命令。输入 help 查看战斗命令。");
            }
        }
    }

    private void handleReward() {
        BattleReward reward = controller.getCurrentReward().orElseThrow();
        System.out.println("\n=== 战斗奖励 ===");
        System.out.println("金币：" + reward.gold());
        if (reward.cardChoices().isEmpty()) {
            System.out.println("本次没有可选卡牌，输入 skip 领取金币。");
        } else {
            System.out.println("卡牌选择：");
            for (int i = 0; i < reward.cardChoices().size(); i++) {
                Card card = reward.cardChoices().get(i);
                System.out.println("  " + i + " - " + card.label()
                        + " | " + card.description());
            }
        }

        String input = readLine("输入卡牌编号，或输入 skip：");
        if (!running) {
            return;
        }
        try {
            if (input.equalsIgnoreCase("skip")) {
                controller.skipRewardCard();
                System.out.println("已跳过卡牌，领取 " + reward.gold() + " 金币。");
                return;
            }

            int choice = Integer.parseInt(input);
            if (choice < 0 || choice >= reward.cardChoices().size()) {
                System.out.println("奖励编号超出范围。");
                return;
            }
            Card card = reward.cardChoices().get(choice);
            controller.claimRewardCard(card.id());
            System.out.println("获得卡牌「" + card.name() + "」和 "
                    + reward.gold() + " 金币。");
        } catch (NumberFormatException exception) {
            System.out.println("请输入奖励编号或 skip。");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("无法领取奖励：" + exception.getMessage());
        }
    }

    private void handlePlaceholderLevel() {
        GamePhase phase = controller.getPhase();
        System.out.println("\n=== " + phaseName(phase) + "节点 ===");
        System.out.println("该模块暂时使用 MVP 占位流程。");
        String input = readLine("输入 complete 完成节点：");
        if (!running) {
            return;
        }
        if (input.equalsIgnoreCase("complete")) {
            controller.onLevelFinished(LevelResult.COMPLETED);
        } else {
            System.out.println("请输入 complete，或输入 quit 退出。");
        }
    }

    private void playCard(Combat combat, String indexText) {
        try {
            int handIndex = Integer.parseInt(indexText);
            PlayCardResult result = combat.playCard(handIndex);
            if (result != PlayCardResult.SUCCESS) {
                System.out.println("出牌失败：" + result);
            }
        } catch (NumberFormatException exception) {
            System.out.println("用法：play <手牌编号>，例如 play 0");
        }
    }

    private void printBattleState(Combat combat) {
        System.out.println("\n=== 战斗 · 第 " + combat.getTurnNumber() + " 回合 ===");
        System.out.println("玩家 HP：" + combat.getPlayerHp() + " / "
                + combat.getPlayerMaxHp()
                + "    护甲：" + combat.getPlayerBlock()
                + "    能量：" + combat.getEnergy() + " / "
                + combat.getPlayerMaxEnergy());
        System.out.println("怪物 HP：" + combat.getMonsterHp() + " / "
                + combat.getMonsterMaxHp()
                + "    护甲：" + combat.getMonsterBlock()
                + "    " + combat.getMonsterIntent());
        System.out.println("手牌：");
        for (int i = 0; i < combat.getHand().size(); i++) {
            Card card = combat.getHand().get(i).card();
            System.out.println("  " + i + " - " + card.label()
                    + " | " + card.description());
        }
        if (combat.getHand().isEmpty()) {
            System.out.println("  （空）");
        }
        System.out.println("牌堆：抽牌 " + combat.getDrawPileSize()
                + " / 弃牌 " + combat.getDiscardPileSize()
                + " / 消耗 " + combat.getExhaustPileSize());
    }

    private void printRunSummary(String title) {
        RunState state = controller.getRunState();
        System.out.println("\n=== " + title + " ===");
        System.out.println("到达章节：" + state.getCurrentAct() + " / " + state.getTotalActs());
        System.out.println("剩余生命：" + state.getPlayer().getHealth()
                + " / " + state.getPlayer().getMaxHealth());
        System.out.println("金币：" + state.getGold());
        System.out.println("永久牌组：" + state.getDeck().size() + " 张");
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            running = false;
            System.out.println("\n输入已结束，退出游戏。");
            return "";
        }
        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("quit")) {
            running = false;
            System.out.println("已退出文字流程。");
        }
        return input;
    }

    private static void printBattleHelp() {
        System.out.println("play 0  - 打出编号为 0 的手牌");
        System.out.println("end     - 结束当前回合");
        System.out.println("quit    - 退出文字流程");
    }

    private static List<CardInstance> createStartingDeck() {
        List<Card> definitions = CardLibrary.startingDeck();
        return IntStream.range(0, definitions.size())
                .mapToObj(index -> new CardInstance(
                        "starter-" + (index + 1), definitions.get(index)))
                .toList();
    }

    private static long readSeed(String[] args) {
        if (args.length == 0) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("地图种子必须是 long 整数。", exception);
        }
    }

    private static int readActCount(String[] args) {
        if (args.length < 2) {
            return DEFAULT_ACT_COUNT;
        }
        try {
            int actCount = Integer.parseInt(args[1]);
            if (actCount <= 0) {
                throw new IllegalArgumentException("章节数量必须大于 0。");
            }
            return actCount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("章节数量必须是 int 整数。", exception);
        }
    }

    private static String typeName(MapNode node) {
        return switch (node.type()) {
            case BATTLE -> "战斗";
            case ELITE -> "精英战斗";
            case EVENT -> "事件";
            case REST -> "休息";
            case SHOP -> "商店";
            case BOSS -> "Boss 战";
        };
    }

    private static String phaseName(GamePhase phase) {
        return switch (phase) {
            case EVENT -> "事件";
            case SHOP -> "商店";
            case REST -> "休息";
            default -> throw new IllegalArgumentException("不是占位关卡阶段: " + phase);
        };
    }

    private static void printUsage() {
        System.out.println("运行参数：GameFlowDebugMain [地图种子] [章节数量]");
        System.out.println("示例：GameFlowDebugMain 12345 1");
    }
}
