package com.roguelike.dungeon.ui;

import com.roguelike.dungeon.flow.GameController;
import com.roguelike.dungeon.flow.GamePhase;
import com.roguelike.dungeon.flow.MenuController;
import com.roguelike.dungeon.flow.MenuPhase;
import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.campfire.CampfireAction;
import com.roguelike.dungeon.game.campfire.CampfireActionResult;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.event.EventChoiceResult;
import com.roguelike.dungeon.game.event.GameEvent;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.map.MapTextRenderer;
import com.roguelike.dungeon.ui.battle.BattleView;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.run.RunState;
import com.roguelike.dungeon.game.shop.ShopActionResult;
import com.roguelike.dungeon.game.shop.ShopItem;
import com.roguelike.dungeon.game.shop.ShopService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 覆盖选角到通关的简易 JavaFX 总流程界面。
 *
 * <p>只调用 {@link MenuController} 与 {@link GameController}，不改核心规则。
 * 纯战斗 Demo 仍在 {@code com.roguelike.dungeon.App}。</p>
 */
public class FlowApp extends Application {

    private static final int DEFAULT_ACT_COUNT = 1;

    private final MapTextRenderer mapRenderer = new MapTextRenderer();
    private final Label titleLabel = new Label("杀戮尖塔 · 简易流程");
    private final Label statusLabel = new Label();
    private final Label hintLabel = new Label();
    private final TextArea bodyArea = new TextArea();
    private final FlowPane actionBox = new FlowPane(8, 8);
    private final HBox handBox = new HBox(8);
    private final TextArea logArea = new TextArea();
    private final TextField characterIdField = new TextField();
    private final TextField seedField = new TextField();
    private final TextField actField = new TextField(String.valueOf(DEFAULT_ACT_COUNT));

    private final StackPane titleLayer = new StackPane();
    private final BorderPane flowPane = new BorderPane();
    private final BattleView battleView = new BattleView(this::log, this::onBattleAction);

    private MenuController menu;
    private GameController controller;
    private String pendingForgeCardId;
    private boolean pickingSmithCard;

    @Override
    public void start(Stage stage) {
        titleLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");
        statusLabel.setWrapText(true);
        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-text-fill: #555555;");

        bodyArea.setEditable(false);
        bodyArea.setWrapText(true);
        bodyArea.setStyle("-fx-font-family: Consolas, 'Microsoft YaHei', monospace;");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth(280);
        logArea.setPromptText("流程日志");

        actionBox.setAlignment(Pos.CENTER_LEFT);
        handBox.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10,
                titleLabel,
                statusLabel,
                bodyArea,
                hintLabel,
                new Label("操作"),
                actionBox,
                new Label("手牌 / 目标"),
                handBox);
        center.setPadding(new Insets(12));
        VBox.setVgrow(center, Priority.ALWAYS);

        ScrollPane logScroll = new ScrollPane(logArea);
        logScroll.setFitToWidth(true);
        logScroll.setFitToHeight(true);
        logScroll.setPrefWidth(300);

        flowPane.setCenter(center);
        flowPane.setRight(logScroll);
        BorderPane.setMargin(logScroll, new Insets(12, 12, 12, 0));

        battleView.setVisible(false);
        battleView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane gameStack = new StackPane(flowPane, battleView);
        buildTitleLayer();

        StackPane root = new StackPane(gameStack, titleLayer);
        Scene scene = new Scene(root, 1280, 720);
        var css = getClass().getResource("/ui/flow.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        resetToTitle();

        stage.setTitle("Save the Spire");
        stage.setScene(scene);
        stage.show();
    }

    private void buildTitleLayer() {
        titleLayer.getStyleClass().add("title-root");
        applyTitleBackground(titleLayer);

        Region dim = new Region();
        dim.getStyleClass().add("title-dim");
        dim.setMouseTransparent(true);

        Button explore = new Button("探索高塔");
        explore.getStyleClass().add("title-button");
        explore.setOnAction(event -> exploreTower());

        Button leave = new Button("怯懦离开");
        leave.getStyleClass().addAll("title-button", "title-button-leave");
        leave.setOnAction(event -> leaveCowardly());

        VBox buttons = new VBox(14, explore, leave);
        buttons.getStyleClass().add("title-buttons");
        buttons.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setAlignment(buttons, Pos.BOTTOM_LEFT);

        titleLayer.getChildren().addAll(dim, buttons);
    }

    private void applyTitleBackground(Region target) {
        var imageUrl = getClass().getResource("/ui/title-spire.png");
        if (imageUrl == null) {
            return;
        }
        Image image = new Image(imageUrl.toExternalForm());
        BackgroundSize cover = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, false, true);
        target.setBackground(new Background(new BackgroundImage(
                image,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                cover)));
    }

    private void resetToTitle() {
        menu = new MenuController(new GameCharacterCatalog());
        controller = null;
        pendingForgeCardId = null;
        pickingSmithCard = false;
        logArea.clear();
        showFlowUi();
        titleLayer.setVisible(true);
        titleLayer.setMouseTransparent(false);
    }

    private void exploreTower() {
        if (menu.getPhase() == MenuPhase.MAIN_MENU) {
            menu.beginCharacterSelect();
        }
        titleLayer.setVisible(false);
        titleLayer.setMouseTransparent(true);
        render();
    }

    private void leaveCowardly() {
        menu.exit();
        Platform.exit();
    }

    private void onBattleAction() {
        if (controller != null
                && controller.getPhase() == GamePhase.BATTLE
                && controller.getCurrentCombat().isPresent()) {
            battleView.refresh();
            return;
        }
        render();
    }

    private void showBattleUi(Combat combat) {
        flowPane.setVisible(false);
        battleView.setVisible(true);
        MapNodeType nodeType = controller.getCurrentNode()
                .map(MapNode::type)
                .orElse(MapNodeType.BATTLE);
        battleView.bind(combat, nodeType);
    }

    private void showFlowUi() {
        battleView.setVisible(false);
        battleView.unbind();
        flowPane.setVisible(true);
    }

    private void render() {
        actionBox.getChildren().clear();
        handBox.getChildren().clear();
        if (controller == null || controller.getPhase() != GamePhase.BATTLE) {
            pendingForgeCardId = null;
        }
        if (controller == null) {
            pickingSmithCard = false;
            showFlowUi();
            renderCharacterSelect();
            return;
        }
        if (controller.getPhase() != GamePhase.REST) {
            pickingSmithCard = false;
        }
        if (controller.getPhase() == GamePhase.BATTLE) {
            Combat combat = controller.getCurrentCombat().orElseThrow();
            showBattleUi(combat);
            return;
        }
        showFlowUi();
        switch (controller.getPhase()) {
            case MAP -> renderMap();
            case BATTLE -> {
            }
            case REWARD -> renderReward();
            case EVENT -> renderEvent();
            case REST -> renderCampfire();
            case SHOP -> renderShop();
            case VICTORY -> renderEnded("恭喜通关！");
            case DEFEAT -> renderEnded("游戏失败，玩家已倒下。");
        }
    }

    private void renderCharacterSelect() {
        titleLabel.setText("选择角色");
        statusLabel.setText("选一个角色开局。隐藏角色可在下方输入编号（例如 god）。");
        hintLabel.setText("种子留空则随机；章节数量默认 1。");
        bodyArea.setText("可选角色：\n");
        for (CharacterDefinition character : menu.getAvailableCharacters()) {
            bodyArea.appendText(character.id() + " - " + character.name()
                    + "（HP " + character.maxHealth()
                    + "，金币 " + character.startingGold() + "）\n"
                    + "  " + character.description() + "\n\n");
            Button button = new Button(character.name());
            button.setTooltip(new Tooltip(character.description()));
            button.setOnAction(event -> startRun(character.id(),
                    seedField.getText(), actField.getText()));
            actionBox.getChildren().add(button);
        }

        characterIdField.setPromptText("角色编号");
        characterIdField.setPrefWidth(120);
        seedField.setPromptText("地图种子（可选）");
        seedField.setPrefWidth(160);
        actField.setPromptText("章节数");
        actField.setPrefWidth(80);
        Button startButton = new Button("用编号开局");
        startButton.setOnAction(event -> {
            String characterId = characterIdField.getText() == null
                    ? ""
                    : characterIdField.getText().trim();
            if (characterId.isEmpty()) {
                log("请输入角色编号，或直接点上面的角色按钮。");
                return;
            }
            startRun(characterId, seedField.getText(), actField.getText());
        });
        actionBox.getChildren().addAll(
                new Label("编号"), characterIdField,
                new Label("种子"), seedField,
                new Label("章节"), actField,
                startButton);
        Button back = new Button("返回高塔");
        back.setOnAction(event -> resetToTitle());
        actionBox.getChildren().add(back);
    }

    private void startRun(String characterId, String seedText, String actText) {
        try {
            CharacterDefinition character = menu.selectCharacter(characterId.trim());
            long seed = parseSeed(seedText);
            int actCount = parseActCount(actText);
            RunState runState = menu.createRun(character.id(), seed, actCount);
            controller = new GameController(
                    runState,
                    CardLibrary.rewardPoolFor(character.rewardCardIds()),
                    line -> Platform.runLater(() -> log("[战斗] " + line)));
            log("开局角色「" + character.name() + "」，种子 " + seed
                    + "，章节 " + actCount + "。");
            render();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log("无法开局：" + exception.getMessage());
        }
    }

    private void renderMap() {
        pickingSmithCard = false;
        RunState state = controller.getRunState();
        titleLabel.setText("地图 · 第 " + state.getCurrentAct() + " / "
                + state.getTotalActs() + " 章");
        statusLabel.setText(runSummary(state));
        hintLabel.setText("点击可选节点进入。");
        bodyArea.setText(mapRenderer.render(controller.getMapService()));
        bodyArea.appendText("\n可选节点：\n");
        List<MapNode> nodes = controller.getMapService().getAvailableNodes();
        if (nodes.isEmpty()) {
            bodyArea.appendText("  （没有可选节点）\n");
        }
        for (MapNode node : nodes) {
            bodyArea.appendText("  " + node.id() + " - " + typeName(node) + "\n");
            Button button = new Button(node.id() + " " + typeName(node));
            button.setOnAction(event -> {
                try {
                    controller.selectNode(node.id());
                    render();
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    log("无法进入节点：" + exception.getMessage());
                }
            });
            actionBox.getChildren().add(button);
        }
    }

    private void renderBattle() {
        Combat combat = controller.getCurrentCombat().orElseThrow();
        titleLabel.setText("战斗 · 第 " + combat.getTurnNumber() + " 回合");
        statusLabel.setText(battleStatus(combat));
        if (pendingForgeCardId != null) {
            hintLabel.setText("已选锻造，请再点一张手牌作为升级目标；再点锻造可取消。");
        } else {
            hintLabel.setText("点击手牌打出。锻造需要再点一张目标牌。");
        }
        bodyArea.setText(battleBody(combat));

        Button endTurn = new Button("结束回合");
        endTurn.setDisable(!combat.isPlayerTurn() || combat.isFinished());
        endTurn.setOnAction(event -> {
            combat.endPlayerTurn();
            render();
        });
        actionBox.getChildren().add(endTurn);

        for (CardInstance instance : combat.getHand()) {
            Button cardButton = new Button(handLabel(instance));
            cardButton.setTooltip(new Tooltip(instance.displayDescription()));
            boolean selectedForge = instance.id().equals(pendingForgeCardId);
            if (selectedForge) {
                cardButton.setStyle("-fx-border-color: #aa6600; -fx-border-width: 2;");
            }
            cardButton.setDisable(!combat.isPlayerTurn() || !instance.card().playable());
            cardButton.setOnAction(event -> playHandCard(combat, instance));
            handBox.getChildren().add(cardButton);
        }
    }

    private void playHandCard(Combat combat, CardInstance instance) {
        boolean forge = instance.card().id().equals(CardLibrary.FORGE.id());
        if (pendingForgeCardId != null) {
            if (instance.id().equals(pendingForgeCardId)) {
                pendingForgeCardId = null;
                log("已取消锻造。");
                render();
                return;
            }
            PlayCardResult result = combat.playCard(pendingForgeCardId, instance.id());
            pendingForgeCardId = null;
            if (result != PlayCardResult.SUCCESS) {
                log("出牌失败：" + playResultText(result));
            }
            render();
            return;
        }
        if (forge) {
            pendingForgeCardId = instance.id();
            render();
            return;
        }
        PlayCardResult result = combat.playCard(instance.id());
        if (result != PlayCardResult.SUCCESS) {
            log("出牌失败：" + playResultText(result));
        }
        render();
    }

    private void renderReward() {
        BattleReward reward = controller.getCurrentReward().orElseThrow();
        titleLabel.setText("战斗奖励");
        statusLabel.setText(runSummary(controller.getRunState()));
        hintLabel.setText("选一张牌加入牌组，或跳过只拿金币。遗物会随领取/跳过一并获得。");
        StringBuilder body = new StringBuilder("金币：").append(reward.gold()).append('\n');
        if (reward.hasRelic()) {
            body.append("遗物：").append(reward.relic().name())
                    .append("（").append(reward.relic().rarity().displayName()).append("）\n")
                    .append("  ").append(reward.relic().description()).append("\n\n");
        }
        if (reward.cardChoices().isEmpty()) {
            body.append("本次没有可选卡牌。\n");
        } else {
            body.append("卡牌选择：\n");
            for (Card card : reward.cardChoices()) {
                body.append("  ").append(card.label())
                        .append(" | ").append(card.description()).append('\n');
                Button button = new Button("领取 " + card.name());
                button.setTooltip(new Tooltip(card.description()));
                button.setOnAction(event -> {
                    try {
                        controller.claimRewardCard(card.id());
                        log("获得卡牌「" + card.name() + "」和 " + reward.gold() + " 金币。");
                        render();
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        log("无法领取奖励：" + exception.getMessage());
                    }
                });
                actionBox.getChildren().add(button);
            }
        }
        bodyArea.setText(body.toString());
        Button skip = new Button("跳过卡牌");
        skip.setOnAction(event -> {
            try {
                controller.skipRewardCard();
                log("已跳过卡牌，领取 " + reward.gold() + " 金币。");
                render();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                log("无法跳过奖励：" + exception.getMessage());
            }
        });
        actionBox.getChildren().add(skip);
    }

    private void renderEvent() {
        GameEvent event = controller.getCurrentEvent().orElseThrow();
        titleLabel.setText(event.title());
        statusLabel.setText(runSummary(controller.getRunState()));
        hintLabel.setText("选择一个事件选项。");
        StringBuilder body = new StringBuilder(event.description()).append("\n\n");
        List<EventChoice> choices = controller.getCurrentEventChoices();
        for (EventChoice choice : choices) {
            body.append("- ").append(choice.label())
                    .append(" | ").append(choice.description());
            if (!choice.available()) {
                body.append("（不可选：").append(choice.unavailableReason()).append("）");
            }
            body.append('\n');
            Button button = new Button(choice.label());
            button.setDisable(!choice.available());
            button.setTooltip(new Tooltip(choice.available()
                    ? choice.description()
                    : choice.unavailableReason()));
            button.setOnAction(eventClick -> {
                EventChoiceResult result = controller.chooseEventChoice(choice.id());
                log(result.message());
                render();
            });
            actionBox.getChildren().add(button);
        }
        bodyArea.setText(body.toString());
    }

    private void renderCampfire() {
        RunState state = controller.getRunState();
        titleLabel.setText("篝火");
        statusLabel.setText(runSummary(state));
        if (pickingSmithCard) {
            hintLabel.setText("选择一张永久牌组中的卡进行升级。");
            renderSmithChoices();
            return;
        }
        hintLabel.setText("休息回复生命，锻造升级一张牌，离开则结束本节点。");
        StringBuilder body = new StringBuilder("当前生命：")
                .append(state.getPlayer().getHealth())
                .append(" / ")
                .append(state.getPlayer().getMaxHealth())
                .append("\n\n");
        for (CampfireAction action : controller.getCurrentCampfireActions()) {
            body.append("- ").append(action.label())
                    .append(" | ").append(action.description());
            if (!action.available()) {
                body.append("（不可选：").append(action.unavailableReason()).append("）");
            }
            body.append('\n');
            Button button = new Button(action.label());
            button.setDisable(!action.available());
            button.setOnAction(event -> executeCampfire(action));
            actionBox.getChildren().add(button);
        }
        bodyArea.setText(body.toString());
    }

    private void renderSmithChoices() {
        List<CardInstance> cards = controller.getCampfireUpgradeableCards();
        StringBuilder body = new StringBuilder("可升级卡牌：\n");
        if (cards.isEmpty()) {
            body.append("  （没有可升级卡牌）\n");
        }
        for (CardInstance card : cards) {
            body.append("  ").append(card.displayName())
                    .append(" | ").append(card.displayDescription()).append('\n');
            Button button = new Button("升级 " + card.displayName());
            button.setTooltip(new Tooltip(card.displayDescription()));
            button.setOnAction(event -> {
                CampfireActionResult result = controller.smithAtCampfire(card.id());
                log(result.message());
                pickingSmithCard = false;
                render();
            });
            actionBox.getChildren().add(button);
        }
        Button cancel = new Button("返回篝火");
        cancel.setOnAction(event -> {
            pickingSmithCard = false;
            render();
        });
        actionBox.getChildren().add(cancel);
        bodyArea.setText(body.toString());
    }

    private void executeCampfire(CampfireAction action) {
        switch (action.id()) {
            case "rest" -> {
                CampfireActionResult result = controller.restAtCampfire();
                log(result.message());
                render();
            }
            case "smith" -> {
                pickingSmithCard = true;
                render();
            }
            case "leave" -> {
                CampfireActionResult result = controller.leaveCampfire();
                log(result.message());
                render();
            }
            default -> log("未知篝火操作：" + action.id());
        }
    }

    private void renderShop() {
        RunState state = controller.getRunState();
        titleLabel.setText("商店");
        statusLabel.setText(runSummary(state));
        hintLabel.setText("购买卡牌或删除一张永久牌组中的牌，然后离开商店。");
        List<ShopItem> items = controller.getCurrentShopItems();
        List<CardInstance> removable = controller.getShopRemovableCards();
        StringBuilder body = new StringBuilder("卡牌商品（每张 ")
                .append(ShopService.CARD_PRICE).append(" 金币）：\n");
        if (items.isEmpty()) {
            body.append("  （已售罄）\n");
        }
        for (ShopItem item : items) {
            body.append("  ").append(item.card().label())
                    .append(" | ").append(item.card().description()).append('\n');
            Button buy = new Button("买 " + item.card().name() + "（" + item.price() + "）");
            buy.setTooltip(new Tooltip(item.card().description()));
            buy.setOnAction(event -> {
                ShopActionResult result = controller.buyShopItem(item.id());
                log(result == ShopActionResult.SUCCESS
                        ? "购买成功：" + item.card().name()
                        : "购买失败：" + shopResultText(result));
                render();
            });
            actionBox.getChildren().add(buy);
        }
        body.append("\n删卡服务（").append(ShopService.CARD_REMOVAL_PRICE)
                .append(" 金币，每个商店限一次）：")
                .append(controller.isShopCardRemovalUsed() ? "已使用" : "可使用")
                .append('\n');
        for (CardInstance card : removable) {
            body.append("  ").append(card.displayName()).append('\n');
            Button remove = new Button("删除 " + card.displayName());
            remove.setDisable(controller.isShopCardRemovalUsed());
            remove.setOnAction(event -> {
                ShopActionResult result = controller.removeCardAtShop(card.id());
                log(result == ShopActionResult.SUCCESS
                        ? "删除成功：" + card.displayName()
                        : "删除失败：" + shopResultText(result));
                render();
            });
            actionBox.getChildren().add(remove);
        }
        bodyArea.setText(body.toString());
        Button leave = new Button("离开商店");
        leave.setOnAction(event -> {
            controller.leaveShop();
            log("离开商店。");
            render();
        });
        actionBox.getChildren().add(leave);
    }

    private void renderEnded(String title) {
        pickingSmithCard = false;
        RunState state = controller.getRunState();
        titleLabel.setText(title);
        statusLabel.setText(runSummary(state));
        hintLabel.setText("可以返回标题再开一局。");
        bodyArea.setText("到达章节：" + state.getCurrentAct() + " / " + state.getTotalActs()
                + "\n剩余生命：" + state.getPlayer().getHealth()
                + " / " + state.getPlayer().getMaxHealth()
                + "\n金币：" + state.getGold()
                + "\n永久牌组：" + state.getDeck().size() + " 张");
        Button again = new Button("返回高塔");
        again.setOnAction(event -> resetToTitle());
        actionBox.getChildren().add(again);
    }

    private static String runSummary(RunState state) {
        return "HP " + state.getPlayer().getHealth() + " / " + state.getPlayer().getMaxHealth()
                + "    金币 " + state.getGold()
                + "    牌组 " + state.getDeck().size() + " 张"
                + "    遗物 " + state.getPlayer().getRelics().size() + " 件";
    }

    private static String battleStatus(Combat combat) {
        StringBuilder text = new StringBuilder();
        text.append("玩家 HP ").append(combat.getPlayerHp()).append(" / ")
                .append(combat.getPlayerMaxHp())
                .append("    护盾 ").append(combat.getPlayerBlock())
                .append("    能量 ").append(combat.getEnergy()).append(" / ")
                .append(combat.getPlayerMaxEnergy());
        text.append("\n").append(combat.getMonsterName())
                .append(" HP ").append(combat.getMonsterHp()).append(" / ")
                .append(combat.getMonsterMaxHp())
                .append("    护盾 ").append(combat.getMonsterBlock())
                .append("    ").append(combat.getMonsterIntent());
        if (combat.isFinished()) {
            text.append("\n").append(combat.getResultText());
        } else {
            text.append(combat.isPlayerTurn() ? "\n当前：玩家回合" : "\n当前：怪物回合");
        }
        return text.toString();
    }

    private static String battleBody(Combat combat) {
        StringBuilder body = new StringBuilder();
        body.append("牌堆：抽牌 ").append(combat.getDrawPileSize())
                .append(" / 弃牌 ").append(combat.getDiscardPileSize())
                .append(" / 消耗 ").append(combat.getExhaustPileSize())
                .append('\n');
        if (combat.getRelics().isEmpty()) {
            body.append("遗物：无\n");
        } else {
            body.append("遗物：\n");
            combat.getRelics().forEach(relic -> body.append("  - ")
                    .append(relic.name())
                    .append("（").append(relic.rarity().displayName()).append("） | ")
                    .append(relic.description()).append('\n'));
        }
        return body.toString();
    }

    private static String handLabel(CardInstance instance) {
        return instance.effectiveCost() + "费 " + instance.displayName();
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

    private static String playResultText(PlayCardResult result) {
        return switch (result) {
            case SUCCESS -> "成功";
            case NOT_PLAYER_TURN -> "不是玩家回合";
            case INVALID_CARD -> "无效卡牌";
            case NOT_ENOUGH_ENERGY -> "能量不足";
            case CARD_NOT_PLAYABLE -> "这张牌现在不能打出";
            case BATTLE_FINISHED -> "战斗已经结束";
        };
    }

    private static String shopResultText(ShopActionResult result) {
        return switch (result) {
            case SUCCESS -> "成功";
            case ITEM_NOT_FOUND -> "商品不存在";
            case ITEM_ALREADY_SOLD -> "商品已售出";
            case CARD_NOT_FOUND -> "卡牌不存在";
            case INSUFFICIENT_GOLD -> "金币不足";
            case CARD_REMOVAL_ALREADY_USED -> "本商店的删卡服务已使用";
            case SHOP_CLOSED -> "商店已关闭";
        };
    }

    private static long parseSeed(String seedText) {
        if (seedText == null || seedText.isBlank()) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(seedText.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("地图种子必须是整数。", exception);
        }
    }

    private static int parseActCount(String actText) {
        if (actText == null || actText.isBlank()) {
            return DEFAULT_ACT_COUNT;
        }
        try {
            int actCount = Integer.parseInt(actText.trim());
            if (actCount <= 0) {
                throw new IllegalArgumentException("章节数量必须大于 0。");
            }
            return actCount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("章节数量必须是整数。", exception);
        }
    }

    private void log(String line) {
        if (!logArea.getText().isEmpty()) {
            logArea.appendText("\n");
        }
        logArea.appendText(line);
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
