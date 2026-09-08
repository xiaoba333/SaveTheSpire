package com.roguelike.dungeon;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.card.Card;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX 程序入口：简单布局展示血量/护盾、手牌和战斗日志。
 */
public class App extends Application {

    private Combat combat;
    private final Label playerStatus = new Label();
    private final Label monsterStatus = new Label();
    private final Label turnStatus = new Label();
    private final Label energyStatus = new Label();
    private final Label pilesStatus = new Label();
    private final TextArea combatLog = new TextArea();
    private final HBox handBox = new HBox(8);
    private final Button endTurnButton = new Button("结束回合");

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

        // 从上到下：状态、怪物、日志、玩家、手牌、结束回合
        VBox root = new VBox(10,
                turnStatus,
                energyStatus,
                monsterStatus,
                combatLog,
                playerStatus,
                pilesStatus,
                new Label("手牌（点击打出）"),
                handBox,
                endTurnButton);
        root.setPadding(new Insets(12));

        combat = new Combat(line -> combatLog.appendText(line + "\n"));
        refreshView();

        stage.setTitle("回合制卡牌 MVP");
        stage.setScene(new Scene(root, 720, 560));
        stage.show();
    }

    /** 根据当前战斗数据刷新标签、手牌按钮和按钮可用性。 */
    private void refreshView() {
        playerStatus.setText("玩家  HP " + combat.getPlayerHp() + " / " + Combat.PLAYER_MAX_HP
                + "    护盾 " + combat.getPlayerBlock());
        energyStatus.setText("能量 " + combat.getEnergy() + " / " + Combat.PLAYER_MAX_ENERGY);
        monsterStatus.setText("怪物  HP " + combat.getMonsterHp() + " / " + Combat.MONSTER_MAX_HP
                + "    护盾 " + combat.getMonsterBlock()
                + "    " + combat.getMonsterIntent());
        pilesStatus.setText("牌库 " + combat.getDrawPileSize()
                + "    弃牌堆 " + combat.getDiscardPileSize()
                + "    消耗堆 " + combat.getExhaustPileSize());

        if (combat.isFinished()) {
            turnStatus.setText(combat.getResultText());
        } else {
            turnStatus.setText(combat.isPlayerTurn() ? "当前：玩家回合" : "当前：怪物回合");
        }

        handBox.getChildren().clear();
        for (int i = 0; i < combat.getHand().size(); i++) {
            Card card = combat.getHand().get(i);
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

    public static void main(String[] args) {
        launch(args);
    }
}
