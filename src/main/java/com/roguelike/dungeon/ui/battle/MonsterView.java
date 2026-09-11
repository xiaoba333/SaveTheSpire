package com.roguelike.dungeon.ui.battle;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** 怪物立绘：多帧循环或破壳开场，单帧则轻微上下浮动。 */
public final class MonsterView extends StackPane {

    private final ImageView imageView = new ImageView();
    private final List<Image> frames = new ArrayList<>();
    private Timeline motion;
    private Timeline bob;
    private int frameIndex;

    public MonsterView() {
        getStyleClass().add("monster-view");
        imageView.setFitWidth(420);
        imageView.setFitHeight(420);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        setAlignment(Pos.BOTTOM_CENTER);
        getChildren().add(imageView);
        setMaxSize(440, 440);
    }

    public void show(MonsterAnims.Clip clip) {
        stop();
        frames.clear();
        frames.addAll(MonsterAnims.frames(clip.key()));
        frameIndex = 0;
        if (frames.isEmpty()) {
            imageView.setImage(null);
            return;
        }
        imageView.setImage(frames.get(0));
        if (clip.introOnce() && frames.size() > 1) {
            playIntroThenIdle();
        } else if (frames.size() > 1) {
            playLoop();
        } else {
            startBob();
        }
    }

    public void shake() {
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(translateXProperty(), 0)),
                new KeyFrame(Duration.millis(40), new KeyValue(translateXProperty(), 14)),
                new KeyFrame(Duration.millis(80), new KeyValue(translateXProperty(), -12)),
                new KeyFrame(Duration.millis(120), new KeyValue(translateXProperty(), 9)),
                new KeyFrame(Duration.millis(160), new KeyValue(translateXProperty(), -5)),
                new KeyFrame(Duration.millis(220), new KeyValue(translateXProperty(), 0)));
        shake.play();
    }

    public void stop() {
        if (motion != null) {
            motion.stop();
            motion = null;
        }
        if (bob != null) {
            bob.stop();
            bob = null;
        }
        setTranslateX(0);
        setTranslateY(0);
    }

    private void playLoop() {
        motion = new Timeline(new KeyFrame(Duration.millis(420), event -> {
            frameIndex = (frameIndex + 1) % frames.size();
            imageView.setImage(frames.get(frameIndex));
        }));
        motion.setCycleCount(Animation.INDEFINITE);
        motion.play();
        startBob();
    }

    private void playIntroThenIdle() {
        motion = new Timeline(new KeyFrame(Duration.millis(280), event -> {
            if (frameIndex < frames.size() - 1) {
                frameIndex++;
                imageView.setImage(frames.get(frameIndex));
            }
        }));
        motion.setCycleCount(Math.max(1, frames.size() - 1));
        motion.setOnFinished(event -> startBob());
        motion.play();
    }

    private void startBob() {
        if (bob != null) {
            bob.stop();
        }
        bob = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(translateYProperty(), 0)),
                new KeyFrame(Duration.millis(900), new KeyValue(translateYProperty(), -8)),
                new KeyFrame(Duration.millis(1800), new KeyValue(translateYProperty(), 0)));
        bob.setCycleCount(Animation.INDEFINITE);
        bob.play();
    }
}
