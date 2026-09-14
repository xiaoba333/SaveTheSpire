package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;
import com.roguelike.dungeon.game.enemy.bestiary.ActOneExtraBestiary;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCatalog;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCategory;
import com.roguelike.dungeon.game.enemy.encounter.EncounterDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 按地图节点挑选第一章怪物：普通小怪池、精英、Boss 蛋链。
 *
 * <p>怪物数据分别来自 {@link ActOneBestiary}（原有 11 只）与
 * {@link ActOneExtraBestiary}（扩充的 6 只），这里只负责「按节点类型挑谁上场」。</p>
 *
 * <h2>两条挑选路径</h2>
 *
 * <ul>
 *   <li><b>单怪路径</b>：{@link #randomEasy(long)} / {@link #elite()} / {@link #boss()}
 *       返回 {@link ScriptedMonsterAi}，只出一只，行为与历史版本一致。</li>
 *   <li><b>编队路径</b>：{@link #randomEncounter(long)} / {@link #encounter(String)}
 *       返回 {@link MonsterEncounterAi}，按 {@code EncounterCatalog} 里的编队出场——
 *       这里就会出现设计案写好的双怪组合（两条蛆、探险者二人组），
 *       玩家需要选择先打哪一只。</li>
 * </ul>
 */
public final class MonsterCatalog {

    /**
     * 普通战斗可出现的怪物池。
     *
     * <p>前四项是原有小怪，后三项由 {@code ActOneExtraBestiary} 补充；
     * 刻意保持「肉盾 / 高频 / 上毒 / 纯直伤」四类都能被抽到，
     * 避免连续几场都是同一种打法。</p>
     */
    private static final List<String> NORMAL_IDS = new ArrayList<>(List.of(
            "grub",
            "wraith",
            "skeleton",
            "explorer_female",
            "fungal_shambler",
            "plague_rat",
            "iron_husk"));

    private MonsterCatalog() {
    }

    static {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
    }

    /** 普通战斗：从普通怪池里按种子等概率抽一只。 */
    public static MonsterAi randomEasy(long seed) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        String id = NORMAL_IDS.get(new Random(seed).nextInt(NORMAL_IDS.size()));
        return ScriptedMonsterAi.of(id);
    }

    /** 精英：巨人遗骸。 */
    public static MonsterAi elite() {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        return ScriptedMonsterAi.of("giant_remains");
    }

    /**
     * 精英（按种子随机）：在原有「巨人遗骸」之外加入镜像幽魂与石哨兵。
     *
     * <p>与 {@link #elite()} 并存，调用方可以按需切换到随机精英；
     * 默认路径仍是固定巨人遗骸，避免影响既有存档与测试的复现性。</p>
     */
    public static MonsterAi randomElite(long seed) {
        ActOneExtraBestiary.init();
        List<String> elites = new ArrayList<>(List.of("giant_remains"));
        elites.addAll(ActOneExtraBestiary.eliteMonsterIds());
        String id = elites.get(new Random(seed).nextInt(elites.size()));
        return ScriptedMonsterAi.of(id);
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
        ActOneExtraBestiary.init();
        return ScriptedMonsterAi.of("kairos_egg_1", smashAlmostCrackedEgg);
    }

    /**
     * Boss（按种子随机）：在凯洛斯之外加入骸骨暴君，构成一层双 Boss 选项。
     *
     * <p>默认路径仍是 {@link #boss()} 的凯洛斯，切换由流程模块决定。</p>
     */
    public static MonsterAi randomBoss(long seed) {
        ActOneExtraBestiary.init();
        List<String> bosses = new ArrayList<>(List.of("kairos_egg_1"));
        bosses.addAll(ActOneExtraBestiary.bossMonsterIds());
        String id = bosses.get(new Random(seed).nextInt(bosses.size()));
        return ScriptedMonsterAi.of(id);
    }

    // ==================================================================
    // 编队路径：支持一节点多只敌人
    // ==================================================================

    /**
     * 普通战斗：从第一层<b>全部</b>普通遭遇里按种子等概率抽一组。
     *
     * <p>与 {@link #randomEasy(long)} 的区别是返回编队而不是单只怪，
     * 因此可能抽到 {@code act1_grubs}（两条蛆）或 {@code act1_explorers}
     * （探险者二人组）这类多敌组合。</p>
     */
    public static MonsterAi randomEncounter(long seed) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        List<EncounterDefinition> pool = EncounterCatalog.byCategory(
                EncounterCategory.NORMAL, ActOneBestiary.ACT);
        if (pool.isEmpty()) {
            throw new IllegalStateException("普通遭遇池为空，检查图鉴注册");
        }
        return encounter(pool.get(new Random(seed).nextInt(pool.size())));
    }

    /** 指定遭遇 id 出场（精英 / Boss / 特定测试用）。 */
    public static MonsterAi encounter(String encounterId) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        return MonsterEncounterAi.ofEncounter(encounterId);
    }

    /** 用已解析的遭遇定义装配战斗。 */
    public static MonsterAi encounter(EncounterDefinition definition) {
        return MonsterEncounterAi.ofEncounter(definition.id());
    }

    /**
     * 按节点难度档位抽一个编队：普通走全池，精英与 Boss 走各自的遭遇池。
     *
     * <p>这张池子抽出来的可能是多只怪，流程层用它可以一次性拿到完整编队。</p>
     */
    public static MonsterAi randomEncounter(EncounterCategory category, long seed) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        List<EncounterDefinition> pool = EncounterCatalog.byCategory(
                category, ActOneBestiary.ACT);
        if (pool.isEmpty()) {
            throw new IllegalStateException(category + " 遭遇池为空，检查图鉴注册");
        }
        return encounter(pool.get(new Random(seed).nextInt(pool.size())));
    }

    /** 多怪遭遇的 id 列表，便于调试与测试直接引用。 */
    public static List<String> multiMonsterEncounterIds() {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        return EncounterCatalog.byCategory(EncounterCategory.NORMAL, ActOneBestiary.ACT)
                .stream()
                .filter(definition -> definition.size() > 1)
                .map(EncounterDefinition::id)
                .toList();
    }
}
