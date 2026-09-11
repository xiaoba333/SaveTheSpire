package com.roguelike.dungeon.ui.battle;

import com.roguelike.dungeon.game.map.MapNodeType;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.List;

/** 把当前战斗怪物对到导入的立绘帧。 */
public final class MonsterAnims {

    public record Clip(String key, boolean introOnce) {
    }

    private MonsterAnims() {
    }

    public static Clip clipFor(String monsterName, MapNodeType nodeType) {
        if (monsterName != null) {
            if (monsterName.contains("无暇")) {
                return new Clip("kairos_egg_1", false);
            }
            if (monsterName.contains("轻微破裂")) {
                return new Clip("kairos_egg_2", false);
            }
            if (monsterName.contains("中度破裂")) {
                return new Clip("kairos_egg_3", false);
            }
            if (monsterName.contains("几乎破裂")) {
                return new Clip("kairos_egg_4", false);
            }
            return switch (monsterName) {
                case "蛆" -> new Clip("grub", false);
                case "亡灵" -> new Clip("wraith", false);
                case "骷髅" -> new Clip("skeleton", false);
                case "探险者女" -> new Clip("explorer_female", false);
                case "探险者男" -> new Clip("explorer_male", false);
                case "巨人遗骸" -> new Clip("giant_remains", false);
                case "凯洛斯" -> new Clip("kairos", true);
                default -> fallback(nodeType);
            };
        }
        return fallback(nodeType);
    }

    private static Clip fallback(MapNodeType nodeType) {
        if (nodeType == MapNodeType.BOSS) {
            return new Clip("kairos_egg_1", false);
        }
        if (nodeType == MapNodeType.ELITE) {
            return new Clip("giant_remains", false);
        }
        return new Clip("skeleton", false);
    }

    public static List<Image> frames(String key) {
        List<Image> frames = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            var url = MonsterAnims.class.getResource("/monster/anim/" + key + "/" + index + ".png");
            if (url == null) {
                break;
            }
            frames.add(new Image(url.toExternalForm(), 640, 640, true, true, false));
        }
        if (frames.isEmpty()) {
            var still = MonsterAnims.class.getResource("/monster/act1/" + key + ".png");
            if (still != null) {
                frames.add(new Image(still.toExternalForm(), 640, 640, true, true, false));
            }
        }
        return List.copyOf(frames);
    }
}
