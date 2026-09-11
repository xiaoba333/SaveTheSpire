package com.roguelike.dungeon.ui.battle;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.card.CardType;
import com.roguelike.dungeon.game.map.MapNodeType;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 战斗场景：可拖动手牌、怪物立绘动画、统一刀光震动。背景先用洞穴色占位。
 */
public final class BattleView extends StackPane {

    private final Consumer<String> logger;
    private final Runnable afterAction;
    private final Label playerHpLabel = new Label();
    private final Label energyLabel = new Label();
    private final Label monsterHpLabel = new Label();
    private final Label intentLabel = new Label();
    private final ProgressBar playerHpBar = new ProgressBar();
    private final ProgressBar monsterHpBar = new ProgressBar();
    private final VBox playerHud = new VBox(8);
    private final MonsterView monsterView = new MonsterView();
    private final HBox handBox = new HBox(10);
    private final Pane dragLayer = new Pane();
    private final Pane fxLayer = new Pane();
    private final Button endTurnButton = new Button("结束回合");
    private final Label hintLabel = new Label();
    private final List<CardView> handCards = new ArrayList<>();

    private Combat combat;
    private MapNodeType nodeType = MapNodeType.BATTLE;
    private CardView dragging;
    private CardView ghost;
    private boolean busy;
    private double pressSceneX;
    private double pressSceneY;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragStarted;

    public BattleView(Consumer<String> logger, Runnable afterAction) {
        this.logger = logger;
        this.afterAction = afterAction;
        getStyleClass().add("battle-root");

        playerHud.getStyleClass().add("player-hud");
        playerHud.setAlignment(Pos.TOP_LEFT);
        playerHud.setPadding(new Insets(16));
        Label playerTitle = new Label("玩家");
        playerTitle.getStyleClass().add("hud-title");
        playerHpBar.getStyleClass().add("hp-bar");
        playerHpBar.setPrefWidth(220);
        energyLabel.getStyleClass().add("hud-energy");
        playerHud.getChildren().addAll(playerTitle, playerHpLabel, playerHpBar, energyLabel);

        VBox monsterHud = new VBox(6, intentLabel, monsterHpLabel, monsterHpBar, monsterView);
        monsterHud.setAlignment(Pos.BOTTOM_CENTER);
        monsterHpBar.getStyleClass().add("hp-bar");
        monsterHpBar.setPrefWidth(260);
        intentLabel.getStyleClass().add("intent-label");
        monsterHpLabel.getStyleClass().add("hud-title");

        StackPane arena = new StackPane();
        HBox combatRow = new HBox(40, playerHud, monsterHud);
        combatRow.setAlignment(Pos.BOTTOM_CENTER);
        combatRow.setPadding(new Insets(12, 24, 8, 24));
        HBox.setHgrow(monsterHud, Priority.ALWAYS);
        arena.getChildren().add(combatRow);
        StackPane.setAlignment(combatRow, Pos.CENTER);

        handBox.setAlignment(Pos.CENTER);
        handBox.setPadding(new Insets(8, 12, 16, 12));
        hintLabel.getStyleClass().add("battle-hint");
        hintLabel.setText("把牌拖向怪物或拖出手牌区打出；锻造拖到目标牌上。");

        endTurnButton.getStyleClass().addAll("title-button");
        endTurnButton.setOnAction(event -> endTurn());

        HBox topBar = new HBox(16, hintLabel, new Region(), endTurnButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 18, 6, 18));
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);
        layout.setCenter(arena);
        layout.setBottom(handBox);

        fxLayer.setMouseTransparent(true);
        dragLayer.setMouseTransparent(true);

        getChildren().addAll(layout, fxLayer, dragLayer);
    }

    private String shownMonsterName;

    public void bind(Combat combat, MapNodeType nodeType) {
        boolean newFight = this.combat != combat;
        this.combat = combat;
        this.nodeType = nodeType == null ? MapNodeType.BATTLE : nodeType;
        String monsterName = combat.getMonsterName();
        if (newFight || !monsterName.equals(shownMonsterName)) {
            shownMonsterName = monsterName;
            monsterView.show(MonsterAnims.clipFor(monsterName, this.nodeType));
        }
        refresh();
    }

    public void unbind() {
        combat = null;
        shownMonsterName = null;
        busy = false;
        clearDrag();
        monsterView.stop();
        handBox.getChildren().clear();
        handCards.clear();
    }

    public void refresh() {
        if (combat == null) {
            return;
        }
        playerHpLabel.setText("HP " + combat.getPlayerHp() + " / " + combat.getPlayerMaxHp()
                + "    护盾 " + combat.getPlayerBlock());
        playerHpBar.setProgress(ratio(combat.getPlayerHp(), combat.getPlayerMaxHp()));
        energyLabel.setText("能量 " + combat.getEnergy() + " / " + combat.getPlayerMaxEnergy());
        monsterHpLabel.setText(combat.getMonsterName()
                + "    HP " + combat.getMonsterHp() + " / " + combat.getMonsterMaxHp()
                + "    护盾 " + combat.getMonsterBlock());
        monsterHpBar.setProgress(ratio(combat.getMonsterHp(), combat.getMonsterMaxHp()));
        intentLabel.setText(combat.getMonsterIntent());
        String monsterName = combat.getMonsterName();
        if (!monsterName.equals(shownMonsterName)) {
            shownMonsterName = monsterName;
            monsterView.show(MonsterAnims.clipFor(monsterName, nodeType));
        }
        endTurnButton.setDisable(busy || !combat.isPlayerTurn() || combat.isFinished());
        rebuildHand();
    }

    private void rebuildHand() {
        handBox.getChildren().clear();
        handCards.clear();
        if (combat == null) {
            return;
        }
        for (CardInstance instance : combat.getHand()) {
            CardView card = new CardView(instance);
            installDrag(card);
            boolean playable = combat.isPlayerTurn() && instance.card().playable() && !busy;
            card.setOpacity(playable ? 1 : 0.55);
            card.setDisable(!playable);
            handCards.add(card);
            handBox.getChildren().add(card);
        }
    }

    private void installDrag(CardView card) {
        card.setOnMousePressed(event -> {
            if (busy || combat == null || !combat.isPlayerTurn()) {
                return;
            }
            pressSceneX = event.getSceneX();
            pressSceneY = event.getSceneY();
            Point2D local = card.sceneToLocal(event.getSceneX(), event.getSceneY());
            dragOffsetX = local.getX();
            dragOffsetY = local.getY();
            dragStarted = false;
            dragging = card;
            event.consume();
        });
        card.setOnMouseDragged(event -> {
            if (dragging != card) {
                return;
            }
            double dx = event.getSceneX() - pressSceneX;
            double dy = event.getSceneY() - pressSceneY;
            if (!dragStarted && Math.hypot(dx, dy) > 8) {
                beginDrag(card);
            }
            if (ghost != null) {
                Point2D local = dragLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
                ghost.setLayoutX(local.getX() - dragOffsetX);
                ghost.setLayoutY(local.getY() - dragOffsetY);
            }
            event.consume();
        });
        card.setOnMouseReleased(this::onRelease);
    }

    private void beginDrag(CardView card) {
        dragStarted = true;
        ghost = new CardView(card.instance());
        ghost.setMouseTransparent(true);
        ghost.setScaleX(1.06);
        ghost.setScaleY(1.06);
        ghost.setRotate(-4);
        card.setOpacity(0.25);
        dragLayer.getChildren().add(ghost);
        Point2D origin = dragLayer.sceneToLocal(card.localToScene(0, 0));
        ghost.setLayoutX(origin.getX());
        ghost.setLayoutY(origin.getY());
    }

    private void onRelease(MouseEvent event) {
        CardView source = dragging;
        boolean wasDragging = dragStarted;
        CardView hovered = cardAt(event.getSceneX(), event.getSceneY(), source);
        boolean overMonster = isOverMonster(event.getSceneX(), event.getSceneY());
        boolean playedUp = wasDragging && getScene() != null
                && event.getSceneY() < getScene().getHeight() * 0.68;
        clearDrag();
        if (source == null || combat == null || busy) {
            return;
        }
        if (!wasDragging) {
            tryPlay(source.instance(), null, isAttack(source.instance()));
            return;
        }
        if (hovered != null && isForge(source.instance())) {
            tryPlay(source.instance(), hovered.instance().id(), isAttack(source.instance()));
            return;
        }
        if (hovered != null && isForge(hovered.instance())) {
            tryPlay(hovered.instance(), source.instance().id(), false);
            return;
        }
        if (overMonster || playedUp) {
            tryPlay(source.instance(), null, isAttack(source.instance()));
        }
        event.consume();
    }

    private void tryPlay(CardInstance instance, String forgeTargetId, boolean showAttackFx) {
        if (combat == null || busy) {
            return;
        }
        if (isForge(instance) && forgeTargetId == null) {
            logger.accept("把锻造拖到要升级的手牌上。");
            refresh();
            return;
        }
        busy = true;
        PlayCardResult result = forgeTargetId == null
                ? combat.playCard(instance.id())
                : combat.playCard(instance.id(), forgeTargetId);
        if (result != PlayCardResult.SUCCESS) {
            busy = false;
            logger.accept("出牌失败：" + result);
            refresh();
            return;
        }
        if (showAttackFx) {
            AttackFx.play(fxLayer, monsterView, this::finishAction);
        } else {
            finishAction();
        }
    }

    private void endTurn() {
        if (combat == null || busy || !combat.isPlayerTurn()) {
            return;
        }
        Combat.Intent intent = combat.getMonsterIntentInfo();
        boolean incomingAttack = intent != null && "ATTACK".equals(intent.type());
        busy = true;
        combat.endPlayerTurn();
        if (incomingAttack) {
            AttackFx.play(fxLayer, playerHud, this::finishAction);
        } else {
            finishAction();
        }
    }

    private void finishAction() {
        busy = false;
        afterAction.run();
    }

    private void clearDrag() {
        if (ghost != null) {
            dragLayer.getChildren().remove(ghost);
            ghost = null;
        }
        if (dragging != null) {
            dragging.setOpacity(1);
        }
        dragging = null;
        dragStarted = false;
    }

    private CardView cardAt(double sceneX, double sceneY, CardView exclude) {
        for (CardView card : handCards) {
            if (card == exclude || !card.isVisible()) {
                continue;
            }
            Bounds bounds = card.localToScene(card.getBoundsInLocal());
            if (bounds.contains(sceneX, sceneY)) {
                return card;
            }
        }
        return null;
    }

    private boolean isOverMonster(double sceneX, double sceneY) {
        Bounds bounds = monsterView.localToScene(monsterView.getBoundsInLocal());
        return bounds.contains(sceneX, sceneY);
    }

    private static boolean isForge(CardInstance instance) {
        return instance.card().id().equals(CardLibrary.FORGE.id());
    }

    private static boolean isAttack(CardInstance instance) {
        return instance.card().type() == CardType.ATTACK;
    }

    private static double ratio(int current, int max) {
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, current / (double) max));
    }
}
