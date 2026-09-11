package com.roguelike.dungeon.ui.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按卡牌编号加载桌面导入的牌面；没有对应图时返回 null，由界面改用文字牌。 */
public final class CardArt {

    private static final String[] EXTENSIONS = {".png", ".jpeg", ".jpg"};
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Image MISSING = new WritableImage(1, 1);

    private CardArt() {
    }

    public static Image imageOf(CardInstance instance) {
        Image upgraded = instance.upgraded() ? load(instance.card().id() + "_plus") : null;
        if (upgraded != null) {
            return upgraded;
        }
        return load(instance.card().id());
    }

    private static Image load(String fileStem) {
        Image cached = CACHE.get(fileStem);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        for (String extension : EXTENSIONS) {
            var url = CardArt.class.getResource("/ui/cards/" + fileStem + extension);
            if (url == null) {
                continue;
            }
            Image image = new Image(url.toExternalForm(), 296, 416, true, true, false);
            CACHE.put(fileStem, image);
            return image;
        }
        CACHE.put(fileStem, MISSING);
        return null;
    }
}
