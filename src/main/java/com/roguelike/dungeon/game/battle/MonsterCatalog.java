package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;

import java.util.List;
import java.util.Random;

/**
 * 按地图节点挑选第一章怪物：四种普通小怪、巨人遗骸、凯洛斯蛋链。
 */
public final class MonsterCatalog {

    private static final List<String> NORMAL_IDS = List.of(
            "grub",
            "wraith",
            "skeleton",
            "explorer_female");

    private MonsterCatalog() {
    }

    static {
        ActOneBestiary.init();
    }

    /** 普通战斗：蛆 / 亡灵 / 骷髅 / 探险者女 四选一。 */
    public static MonsterAi randomEasy(long seed) {
        ActOneBestiary.init();
        String id = NORMAL_IDS.get(new Random(seed).nextInt(NORMAL_IDS.size()));
        return ScriptedMonsterAi.of(id);
    }

    /** 精英：巨人遗骸。 */
    public static MonsterAi elite() {
        ActOneBestiary.init();
        return ScriptedMonsterAi.of("giant_remains");
    }

    /** Boss：从无暇蛋开始，破裂后进入凯洛斯体系。 */
    public static MonsterAi boss() {
        ActOneBestiary.init();
        return ScriptedMonsterAi.of("kairos_egg_1");
    }
}
