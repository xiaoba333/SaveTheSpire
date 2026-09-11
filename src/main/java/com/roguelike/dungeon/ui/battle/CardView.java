package com.roguelike.dungeon.ui.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** 一张可拖动的手牌：有牌面用图，没有对应牌面时用文字说明。 */
public final class CardView extends StackPane {

    public static final double WIDTH = 148;
    public static final double HEIGHT = 208;

    private final CardInstance instance;

    public CardView(CardInstance instance) {
        this.instance = instance;
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        getStyleClass().add("hand-card");

        Image art = CardArt.imageOf(instance);
        if (art != null) {
            ImageView view = new ImageView(art);
            view.setFitWidth(WIDTH);
            view.setFitHeight(HEIGHT);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            getChildren().add(view);
        } else {
            getChildren().add(buildTextFace(instance));
        }
    }

    public CardInstance instance() {
        return instance;
    }

    private static VBox buildTextFace(CardInstance instance) {
        Label cost = new Label(instance.effectiveCost() + " 费");
        cost.getStyleClass().add("text-card-cost");
        Label name = new Label(instance.displayName());
        name.getStyleClass().add("text-card-name");
        name.setWrapText(true);
        Label type = new Label("【" + instance.card().type().displayName() + "】");
        type.getStyleClass().add("text-card-type");
        Label desc = new Label(instance.displayDescription());
        desc.getStyleClass().add("text-card-desc");
        desc.setWrapText(true);

        VBox box = new VBox(6, cost, name, type, desc);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(12, 10, 10, 10));
        box.getStyleClass().add("text-card-face");
        box.setPrefSize(WIDTH, HEIGHT);
        return box;
    }
}
