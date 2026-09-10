package com.roguelike.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.CombatFactory;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX 程序入口：简单布局展示血量/护盾、手牌和战斗日志。
 */
public class App extends Application {

    private static final String CARD_BOX_STYLE = """
            -fx-border-color: #333333;
            -fx-border-width: 2;
            -fx-background-color: #f4efe3;
            -fx-padding: 10;
            """;
    private static final String PILE_BUTTON_STYLE = """
            -fx-background-color: #ece6d6;
            -fx-border-color: #333333;
            -fx-border-width: 2;
            -fx-cursor: hand;
            """;

    private Combat combat;
    private final Label playerStatus = new Label();
    private final Label monsterStatus = new Label();
    private final Label turnStatus = new Label();
    private final Label energyStatus = new Label();
    private final Label exhaustStatus = new Label();
    private final TextArea combatLog = new TextArea();
    private final HBox handBox = new HBox(8);
    private final Button endTurnButton = new Button("结束回合");
    private final Button drawPileButton = createPileButton();
    private final Button discardPileButton = createPileButton();
    private final StackPane pileOverlay = new StackPane();
    private final Label pileOverlayTitle = new Label();
    private final Label pileOverlayHint = new Label();
    private final FlowPane pileOverlayCards = new FlowPane(12, 12);

    @Override
    public void start(Stage stage) {
        combatLog.setEditable(false);
        combatLog.setWrapText(true);
        combatLog.setPrefRowCount(12);
        VBox.setVgrow(combatLog, Priority.ALWAYS);

        endTurnButton.setOnAction(event -> {
            combat.endPlayerTurn();
            refreshView();
        });
        drawPileButton.setOnAction(event -> showPileOverlay(
                "抽牌堆",
                "调试：从左到右为抽牌顺序，最左侧为下一张。",
                reversedCopy(combat.getDrawPile())));
        discardPileButton.setOnAction(event -> showPileOverlay(
                "弃牌堆",
                "调试：从左到右为弃入顺序，最右侧为最近一张。",
                combat.getDiscardPile()));

        handBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(handBox, Priority.ALWAYS);

        HBox pileAndHandRow = new HBox(12, drawPileButton, handBox, discardPileButton);
        pileAndHandRow.setAlignment(Pos.CENTER_LEFT);

        VBox gameRoot = new VBox(10,
                turnStatus,
                energyStatus,
                monsterStatus,
                combatLog,
                playerStatus,
                exhaustStatus,
                new Label("手牌（点击打出）· 两侧牌堆可点开查看（调试）"),
                pileAndHandRow,
                endTurnButton);
        gameRoot.setPadding(new Insets(12));

        buildPileOverlay();
        StackPane root = new StackPane(gameRoot, pileOverlay);

        combat = CombatFactory.createDemo(line -> combatLog.appendText(line + "\n"));
        refreshView();

        Scene scene = new Scene(root, 860, 600);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && pileOverlay.isVisible()) {
                hidePileOverlay();
            }
        });

        stage.setTitle("回合制卡牌 MVP");
        stage.setScene(scene);
        stage.show();
    }

    /** 根据当前战斗数据刷新标签、手牌按钮和按钮可用性。 */
    private void refreshView() {
        playerStatus.setText("玩家  HP " + combat.getPlayerHp() + " / " + combat.getPlayerMaxHp()
                + "    护盾 " + combat.getPlayerBlock());
        energyStatus.setText("能量 " + combat.getEnergy() + " / " + combat.getPlayerMaxEnergy());
        monsterStatus.setText(combat.getMonsterName() + "  HP " + combat.getMonsterHp()
                + " / " + combat.getMonsterMaxHp()
                + "    护盾 " + combat.getMonsterBlock()
                + "    " + combat.getMonsterIntent());
        exhaustStatus.setText("消耗堆 " + combat.getExhaustPileSize());

        if (combat.isFinished()) {
            turnStatus.setText(combat.getResultText());
        } else {
            turnStatus.setText(combat.isPlayerTurn() ? "当前：玩家回合" : "当前：怪物回合");
        }

        updatePileButton(drawPileButton, "抽牌堆", combat.getDrawPileSize());
        updatePileButton(discardPileButton, "弃牌堆", combat.getDiscardPileSize());

        handBox.getChildren().clear();
        for (int i = 0; i < combat.getHand().size(); i++) {
            CardInstance instance = combat.getHand().get(i);
            Card card = instance.card();
            Button cardButton = new Button(card.label());
            cardButton.setTooltip(new Tooltip(card.description()));
            final int index = i;
            cardButton.setDisable(!combat.isPlayerTurn() || !card.playable());
            cardButton.setOnAction(event -> {
                combat.playCard(index);
                refreshView();
            });
            handBox.getChildren().add(cardButton);
        }

        endTurnButton.setDisable(!combat.isPlayerTurn());
    }

    private void buildPileOverlay() {
        pileOverlayTitle.setStyle("-fx-font-size: 22; -fx-font-weight: bold;");
        pileOverlayHint.setWrapText(true);

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(event -> hidePileOverlay());

        BorderPane header = new BorderPane();
        header.setLeft(new VBox(4, pileOverlayTitle, pileOverlayHint));
        header.setRight(closeButton);
        BorderPane.setAlignment(closeButton, Pos.TOP_RIGHT);

        pileOverlayCards.setPadding(new Insets(8, 0, 8, 0));
        pileOverlayCards.setPrefWrapLength(720);

        ScrollPane scroll = new ScrollPane(pileOverlayCards);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox panel = new VBox(12, header, scroll);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #faf7f0; -fx-border-color: #333333; -fx-border-width: 2;");
        panel.maxWidthProperty().bind(pileOverlay.widthProperty().multiply(0.92));
        panel.maxHeightProperty().bind(pileOverlay.heightProperty().multiply(0.92));
        panel.prefWidthProperty().bind(pileOverlay.widthProperty().multiply(0.92));
        panel.prefHeightProperty().bind(pileOverlay.heightProperty().multiply(0.92));
        panel.setOnMouseClicked(event -> event.consume());

        pileOverlay.getChildren().add(panel);
        pileOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.55);");
        pileOverlay.setVisible(false);
        pileOverlay.setMouseTransparent(true);
        pileOverlay.setOnMouseClicked(event -> hidePileOverlay());
    }

    private void showPileOverlay(String pileName, String hint, List<Card> cards) {
        pileOverlayTitle.setText(pileName + "（调试）· " + cards.size() + " 张");
        pileOverlayHint.setText(hint);
        pileOverlayCards.getChildren().clear();
        if (cards.isEmpty()) {
            Label empty = new Label("（空）");
            empty.setStyle("-fx-font-size: 16; -fx-text-fill: #666666;");
            pileOverlayCards.getChildren().add(empty);
        } else {
            for (int i = 0; i < cards.size(); i++) {
                pileOverlayCards.getChildren().add(createCardPlaceholder(cards.get(i), i + 1));
            }
        }
        pileOverlay.setVisible(true);
        pileOverlay.setMouseTransparent(false);
    }

    private void hidePileOverlay() {
        pileOverlay.setVisible(false);
        pileOverlay.setMouseTransparent(true);
    }

    private static Button createPileButton() {
        Button button = new Button();
        button.setPrefSize(92, 120);
        button.setFocusTraversable(false);
        button.setStyle(PILE_BUTTON_STYLE);
        return button;
    }

    private static void updatePileButton(Button button, String title, int count) {
        Label titleLabel = new Label(title);
        Label countLabel = new Label(String.valueOf(count));
        countLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold;");
        VBox graphic = new VBox(6, titleLabel, countLabel);
        graphic.setAlignment(Pos.CENTER);
        button.setGraphic(graphic);
        button.setText(null);
    }

    private static VBox createCardPlaceholder(Card card, int order) {
        Label orderLabel = new Label("#" + order);
        orderLabel.setStyle("-fx-text-fill: #666666;");
        Label nameLabel = new Label(card.name());
        nameLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        nameLabel.setWrapText(true);
        Label metaLabel = new Label(card.cost() + "费 · " + card.type().displayName()
                + (card.exhausts() ? " · 消耗" : ""));
        Label descLabel = new Label(card.description());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12;");

        VBox box = new VBox(6, orderLabel, nameLabel, metaLabel, descLabel);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefSize(140, 188);
        box.setMinSize(140, 188);
        box.setStyle(CARD_BOX_STYLE);
        return box;
    }

    private static List<Card> reversedCopy(List<Card> source) {
        List<Card> copy = new ArrayList<>(source);
        Collections.reverse(copy);
        return copy;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
