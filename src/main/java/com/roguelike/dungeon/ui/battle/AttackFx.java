package com.roguelike.dungeon.ui.battle;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/** 统一攻击表现：刀光划过 + 目标平移震动。 */
public final class AttackFx {

    private AttackFx() {
    }

    public static void play(Pane overlay, Node target, Runnable onFinished) {
        Bounds bounds = target.localToScene(target.getBoundsInLocal());
        Point2D topLeft = overlay.sceneToLocal(bounds.getMinX(), bounds.getMinY());
        Point2D bottomRight = overlay.sceneToLocal(bounds.getMaxX(), bounds.getMaxY());
        double fromX = topLeft.getX() + (bottomRight.getX() - topLeft.getX()) * 0.18;
        double fromY = topLeft.getY() + (bottomRight.getY() - topLeft.getY()) * 0.16;
        double toX = bottomRight.getX() - (bottomRight.getX() - topLeft.getX()) * 0.14;
        double toY = bottomRight.getY() - (bottomRight.getY() - topLeft.getY()) * 0.22;

        Line slash = new Line(fromX, fromY, toX, toY);
        slash.setStroke(Color.web("#f7f1dc"));
        slash.setStrokeWidth(7);
        slash.setStrokeLineCap(StrokeLineCap.ROUND);
        slash.setOpacity(0);
        slash.setMouseTransparent(true);
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ffe9a3"));
        glow.setRadius(22);
        slash.setEffect(glow);
        overlay.getChildren().add(slash);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(50), slash);
        fadeIn.setToValue(1);
        ScaleTransition thicken = new ScaleTransition(Duration.millis(90), slash);
        thicken.setFromX(0.15);
        thicken.setToX(1);
        thicken.setFromY(0.35);
        thicken.setToY(1);
        RotateTransition tilt = new RotateTransition(Duration.millis(90), slash);
        tilt.setFromAngle(-10);
        tilt.setToAngle(8);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(160), slash);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition slashAnim = new SequentialTransition(
                new ParallelTransition(fadeIn, thicken, tilt),
                fadeOut);
        shake(target);
        slashAnim.setOnFinished(event -> {
            overlay.getChildren().remove(slash);
            if (onFinished != null) {
                onFinished.run();
            }
        });
        slashAnim.play();
    }

    public static void shake(Node node) {
        double origin = node.getTranslateX();
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), origin)),
                new KeyFrame(Duration.millis(40), new KeyValue(node.translateXProperty(), origin + 14)),
                new KeyFrame(Duration.millis(80), new KeyValue(node.translateXProperty(), origin - 12)),
                new KeyFrame(Duration.millis(120), new KeyValue(node.translateXProperty(), origin + 9)),
                new KeyFrame(Duration.millis(160), new KeyValue(node.translateXProperty(), origin - 5)),
                new KeyFrame(Duration.millis(220), new KeyValue(node.translateXProperty(), origin)));
        shake.play();
    }
}
