package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;

import java.util.List;
import java.util.Random;

/**
 * 按地图节点挑选怪物：普通小怪池、精英、Boss 蛋链。
 *
 * <p>第二层暂与第一层共用同一图鉴。</p>
 *
 * <p>怪物数据分别来自 {@link com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary}
 * （原有 11 只）与扩充图鉴，这里只负责「按节点类型挑谁上场」。</p>
 *
 * <h2>两条挑选路径</h2>
 *
 * <ul>
 *   <li><b>单怪路径</b>：{@link #randomEasy(long)} / {@link #elite()} / {@link #boss()}，
 *   返回 {@link ScriptedMonsterAi}，只出一只，行为与历史版本一致。</li>
 *   <li><b>编队路径</b>：普通战斗会走编队池，因此会出现设计案写好的双怪组合
 *   （两条蛆、探险者二人组），玩家需要选择先打哪一只。
 *   精英与 Boss 暂时保持单怪，避免 Boss 蛋链的形态变换与多怪槽位耦合。</li>
 * </ul>
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
        return boss(false);
    }

    /**
     * Boss。
     *
     * @param smashAlmostCrackedEgg true 时进入「几乎破裂」阶段会立刻打碎该阶段
     */
    public static MonsterAi boss(boolean smashAlmostCrackedEgg) {
        ActOneBestiary.init();
        return ScriptedMonsterAi.of("kairos_egg_1", smashAlmostCrackedEgg);
    }
}
