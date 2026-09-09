package com.roguelike.dungeon.ui.map;

import com.roguelike.dungeon.game.map.DungeonMap;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeState;
import com.roguelike.dungeon.game.map.MapProgress;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;

/** JavaFX 地图页面，负责绘制路线和关卡按钮。 */
public final class MapView extends BorderPane {
    private static final double COLUMN_GAP = 150;
    private static final double FLOOR_GAP = 110;
    private static final double MARGIN = 70;
    private static final double NODE_SIZE = 64;

    private final DungeonMap dungeonMap;
    private final MapProgress progress;
    private final Consumer<Integer> nodeSelectionHandler;
    private final Pane mapPane = new Pane();

    public MapView(
            DungeonMap dungeonMap,
            MapProgress progress,
            Consumer<Integer> nodeSelectionHandler) {
        this.dungeonMap = Objects.requireNonNull(dungeonMap, "地图不能为 null");
        this.progress = Objects.requireNonNull(progress, "地图进度不能为 null");
        this.nodeSelectionHandler = Objects.requireNonNull(
                nodeSelectionHandler, "节点点击处理器不能为 null");

        ScrollPane scrollPane = new ScrollPane(mapPane);
        scrollPane.setFitToWidth(true);
        setCenter(scrollPane);
        refresh();
    }

    /** 根据最新进度重新绘制地图。 */
    public void refresh() {
        mapPane.getChildren().clear();
        sizeMapPane();
        drawConnections();
        drawNodes();
    }

    private void drawConnections() {
        for (MapNode node : dungeonMap.getNodes()) {
            for (int nextNodeId : node.nextNodeIds()) {
                MapNode target = dungeonMap.getNode(nextNodeId);
                Line line = new Line(
                        nodeCenterX(node), nodeCenterY(node),
                        nodeCenterX(target), nodeCenterY(target));
                line.setStyle("-fx-stroke: #777777; -fx-stroke-width: 3;");
                mapPane.getChildren().add(line);
            }
        }
    }

    private void drawNodes() {
        for (MapNode node : dungeonMap.getNodes()) {
            MapNodeState state = progress.getState(node.id());
            Button button = new Button(typeLabel(node));
            button.setPrefSize(NODE_SIZE, NODE_SIZE);
            button.setLayoutX(nodeCenterX(node) - NODE_SIZE / 2);
            button.setLayoutY(nodeCenterY(node) - NODE_SIZE / 2);
            button.setDisable(state != MapNodeState.AVAILABLE);
            button.setStyle(styleFor(state));
            button.setTooltip(new Tooltip("第 " + (node.floor() + 1)
                    + " 层 · " + node.type()));
            button.setOnAction(event -> nodeSelectionHandler.accept(node.id()));
            mapPane.getChildren().add(button);
        }
    }

    private void sizeMapPane() {
        int maxColumn = dungeonMap.getNodes().stream()
                .mapToInt(MapNode::column)
                .max()
                .orElse(0);
        mapPane.setMinWidth(MARGIN * 2 + maxColumn * COLUMN_GAP + NODE_SIZE);
        mapPane.setMinHeight(MARGIN * 2
                + dungeonMap.getMaxFloor() * FLOOR_GAP + NODE_SIZE);
    }

    private double nodeCenterX(MapNode node) {
        return MARGIN + NODE_SIZE / 2 + node.column() * COLUMN_GAP;
    }

    private double nodeCenterY(MapNode node) {
        return MARGIN + NODE_SIZE / 2
                + (dungeonMap.getMaxFloor() - node.floor()) * FLOOR_GAP;
    }

    private static String typeLabel(MapNode node) {
        return switch (node.type()) {
            case BATTLE -> "战斗";
            case ELITE -> "精英";
            case EVENT -> "?";
            case REST -> "休息";
            case SHOP -> "商店";
            case BOSS -> "Boss";
        };
    }

    private static String styleFor(MapNodeState state) {
        return switch (state) {
            case LOCKED -> "-fx-background-color: #666666; -fx-text-fill: white;";
            case AVAILABLE -> "-fx-background-color: #93c47d; -fx-font-weight: bold;";
            case CURRENT -> "-fx-background-color: #ffd966; -fx-font-weight: bold;";
            case COMPLETED -> "-fx-background-color: #b7b7b7;";
        };
    }
}
