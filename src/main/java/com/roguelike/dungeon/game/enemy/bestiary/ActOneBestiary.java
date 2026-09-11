package com.roguelike.dungeon.game.enemy.bestiary;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.roguelike.dungeon.game.enemy.MonsterCatalog;
import com.roguelike.dungeon.game.enemy.MonsterDefinition;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCatalog;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCategory;
import com.roguelike.dungeon.game.enemy.encounter.EncounterDefinition;
import com.roguelike.dungeon.game.enemy.encounter.EncounterDefinition.MonsterSpawn;
import com.roguelike.dungeon.game.enemy.intent.Intents;
import com.roguelike.dungeon.game.enemy.script.EnemyScript;
import com.roguelike.dungeon.game.enemy.script.PhaseScript;
import com.roguelike.dungeon.game.enemy.script.Scripts;
import com.roguelike.dungeon.game.enemy.status.RevivalStatus;
import com.roguelike.dungeon.game.enemy.status.StatusIds;

/**
 * 第一层「地牢外围」图鉴：把《怪物ai设计案.docx》里的数值与意图逐条落成数据。
 *
 * <p>本类是怪物数据的<b>唯一出处</b>。策划改数值只改这里，框架与战斗模块都不用动。</p>
 *
 * <h3>数据来源对照</h3>
 * <pre>
 * 普通怪
 *   蛆（设计案标题为「两条蛆」）  各 12 血，初始「蠕动」，循环打6 / 上虚弱（交错）
 *   亡灵                        20 血，第1回合给玩家99层易伤，之后打6 / 防6并获得2力量
 *   骷髅                        35 血，初始「骨质疏松」，1-2循环：打12 / 回6
 *   探险者男                    30 血，给女探险者上10甲 / 打6 / 打12
 *   探险者女                    20 血，打2*3 / 两人都加一力量 / 给两人回6血
 * 精英怪
 *   巨人遗骸                    75 血，初始「无灵」，1-2循环：打6+1易伤 / 打12
 *                               血量 ≤ 20 → 无灵消失，获得「复苏」，2-3循环：回复10血 / 打6+1易伤 / 打12
 * Boss
 *   凯洛斯的蛋（无暇/轻微破裂/中度破裂/几乎破裂） 10/20/30/40 血，意图为「破裂」，每破裂一次给凯洛斯 +1 层蜕变
 *   凯洛斯（破壳而出）          100 血，打0*9 / 向玩家抽牌堆塞甲片 / 打12
 * </pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ActOneBestiary.init();                       // 战斗模块启动时调一次即可，重复调用安全
 * List<Monster> monsters = EncounterCatalog
 *         .require("act1_grubs")
 *         .createMonsters();
 * }</pre>
 */
public final class ActOneBestiary {

    /** 本层编号。 */
    public static final int ACT = 1;

    /** Boss 用来塞进玩家抽牌堆的卡牌 ID（卡牌定义由卡牌负责人维护）。 */
    public static final String CARD_SCALE_SHARD = "scale_shard";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private ActOneBestiary() {
    }

    // ==================================================================
    // 初始化
    // ==================================================================

    /**
     * 把第一层全部怪物与遭遇注册进目录。重复调用安全（幂等）。
     *
     * <p>战斗模块在启动时调用一次；如果忘了调用，{@code MonsterCatalog.require}
     * 会抛出带提示的异常，不会静默失败。</p>
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        registerMonsters();
        registerEncounters();
    }

    /** 是否已完成初始化（测试用）。 */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    /** 强制重新加载，仅供测试与 Demo 反复运行。 */
    public static void reload() {
        MonsterCatalog.clear();
        EncounterCatalog.clear();
        INITIALIZED.set(false);
        init();
    }

    // ==================================================================
    // 怪物定义
    // ==================================================================

    // ------------------------------ 普通怪 ------------------------------

    /**
     * 蛆（设计案标题写作「两条蛆」）：
     * 各 12 血，初始拥有状态「蠕动」（不会随回合数减少，首次受到攻击时伤害减半，受击后消失）。
     *
     * <p>意图：循环打6，给玩家一层虚弱（交错进行）。</p>
     *
     * <p>「交错」用 variant 实现：0 号从「打6」起手，1 号从「上虚弱」起手，
     * 于是一回合里玩家总能看到一次攻击 + 一次削弱。</p>
     */
    public static final MonsterDefinition GRUB = MonsterDefinition
            .builder("grub", "蛆", 12)
            .sprite("act1/grub")
            .status(StatusIds.CREEPING, 1)
            .script(variant -> Scripts.alternate(
                    Intents.attack(6),
                    Intents.debuffPlayer(StatusIds.WEAK, 1),
                    variant))
            .tag("act1", "normal", "vermin")
            .build();

    /**
     * 亡灵：20 血。
     * 意图：（1）给予玩家99层易伤 → 之后 2-3 循环「打6 / 防6并获得2力量」。
     *
     * <p class="note">待确认：99 层易伤几乎等于直接斩杀玩家，疑似策划笔误（可能是 1 层或 9 层）。
     * 框架按设计案原文录入，改数值只需动这一行。</p>
     */
    public static final MonsterDefinition WRAITH = MonsterDefinition
            .builder("wraith", "亡灵", 20)
            .sprite("act1/wraith")
            .script(variant -> Scripts.loopFrom(1,
                    Intents.debuffPlayer(StatusIds.VULNERABLE, 99),
                    Intents.attack(6),
                    Intents.blockAndStrength(6, 2)))
            .tag("act1", "normal", "undead")
            .build();

    /**
     * 骷髅：35 血，初始拥有状态「骨质疏松」
     * （受到伤害时额外受到两点伤害，且每次行动都会受到 2 点伤害）。
     *
     * <p>意图：（1）打12 → 1-2 循环「打12 / 回6」。</p>
     */
    public static final MonsterDefinition SKELETON = MonsterDefinition
            .builder("skeleton", "骷髅", 35)
            .sprite("act1/skeleton")
            .status(StatusIds.OSTEOPOROSIS, 1)
            .script(variant -> Scripts.loop(
                    Intents.attack(12),
                    Intents.healSelf(6)))
            .tag("act1", "normal", "undead")
            .build();

    /**
     * 探险者男：30 血。
     * 意图：（1）给女探险者上10甲 →（2）打6 →（3）打12。
     *
     * <p class="note">待确认：设计案未标注循环范围，此处按「1-2-3 循环」实现，
     * 也就是每三回合重新为女探险者补一次甲。</p>
     */
    public static final MonsterDefinition EXPLORER_MALE = MonsterDefinition
            .builder("explorer_male", "探险者男", 30)
            .sprite("act1/explorer_male")
            .script(variant -> Scripts.loop(
                    Intents.giveAllyBlock("explorer_female", 10),
                    Intents.attack(6),
                    Intents.attack(12)))
            .tag("act1", "normal", "human")
            .build();

    /**
     * 探险者女：20 血。
     * 意图：（1）打2*3（2伤害3次）→（2）两人都加一力量 →（3）给两人回6血。
     *
     * <p class="note">待确认：同探险者男，未标注循环范围，此处按「1-2-3 循环」实现。</p>
     */
    public static final MonsterDefinition EXPLORER_FEMALE = MonsterDefinition
            .builder("explorer_female", "探险者女", 20)
            .sprite("act1/explorer_female")
            .script(variant -> Scripts.loop(
                    Intents.attack(2, 3),
                    Intents.buffAlliesStrength(1),
                    Intents.healAllies(6)))
            .tag("act1", "normal", "human")
            .build();

    // ------------------------------ 精英怪 ------------------------------

    /**
     * 巨人遗骸：75 血，初始拥有状态「无灵」（无法被易伤、虚弱、中毒）。
     *
     * <p>第一阶段意图：1-2 循环「打6并给予1层易伤 / 打12」。</p>
     * <p>血量跌落到 20（含）以下时：无灵状态消失，获得状态「复苏」（所有行动判定两次），
     * 意图更变为 2-3 循环「回复10血 / 打6并给予1层易伤 / 打12」。</p>
     */
    public static final MonsterDefinition GIANT_REMAINS = MonsterDefinition
            .builder("giant_remains", "巨人遗骸", 75)
            .sprite("act1/giant_remains")
            .status(StatusIds.SOULLESS, 1)
            .script(variant -> Scripts
                    .phase(phaseOneIntents())
                    .addPhase(PhaseScript.Phase.of(
                            "巨人遗骸 的血量跌落 20，无灵状态消失，获得状态「复苏」",
                            PhaseScript.healthAtMost(20),
                            Scripts.loopFrom(1,
                                    Intents.healSelf(10),
                                    Intents.attackWithStatus(6, StatusIds.VULNERABLE, 1),
                                    Intents.attack(12)),
                            List.of(new RevivalStatus(1)),
                            List.of(StatusIds.SOULLESS))))
            .tag("act1", "elite", "undead")
            .build();

    private static EnemyScript phaseOneIntents() {
        return Scripts.loop(
                Intents.attackWithStatus(6, StatusIds.VULNERABLE, 1),
                Intents.attack(12));
    }

    // ------------------------------ Boss ------------------------------

    /** 凯洛斯的蛋（无暇）：10 血。 */
    public static final MonsterDefinition KAIROS_EGG_1 =
            egg("kairos_egg_1", "凯洛斯的蛋（无暇）", 10, "kairos_egg_2");

    /** 凯洛斯的蛋（轻微破裂）：20 血。 */
    public static final MonsterDefinition KAIROS_EGG_2 =
            egg("kairos_egg_2", "凯洛斯的蛋（轻微破裂）", 20, "kairos_egg_3");

    /** 凯洛斯的蛋（中度破裂）：30 血。 */
    public static final MonsterDefinition KAIROS_EGG_3 =
            egg("kairos_egg_3", "凯洛斯的蛋（中度破裂）", 30, "kairos_egg_4");

    /** 凯洛斯的蛋（几乎破裂）：40 血，下一次破裂直接孵出凯洛斯。 */
    public static final MonsterDefinition KAIROS_EGG_4 =
            egg("kairos_egg_4", "凯洛斯的蛋（几乎破裂）", 40, "kairos");

    /**
     * 凯洛斯（一条甲龙，破壳而出）：100 血。
     *
     * <p>意图：（1）打0*9（0伤害9次）→（2）向玩家抽牌堆增加一张甲片 →（3）打12。</p>
     *
     * <p>「打0*9」的 0 点基础伤害是刻意设计的：实际伤害完全来自力量，
     * 蛋每破裂一次给 1 层「蜕变」，四次破裂后凯洛斯带着 4 点力量出场，
     * 于是这一招变成 9 段各 4 点——正好是「早点打蛋 / 晚点打蛋」的抉择。</p>
     *
     * <p class="note">待确认：设计案未标注循环范围，此处按「1-2-3 循环」实现。</p>
     */
    public static final MonsterDefinition KAIROS = MonsterDefinition
            .builder("kairos", "凯洛斯", 100)
            .sprite("act1/kairos")
            .script(variant -> Scripts.loop(
                    Intents.attack(0, 9),
                    Intents.addCardToPlayerDrawPile(CARD_SCALE_SHARD, "甲片"),
                    Intents.attack(12)))
            .tag("act1", "boss", "dragon")
            .build();

    /**
     * 蛋形态工厂：唯一意图就是「破裂」——杀死自身并把一层蜕变交给下一个形态。
     *
     * @param id         蛋形态 ID
     * @param displayName 展示名
     * @param health     该形态血量
     * @param nextFormId 破裂后变成谁
     */
    private static MonsterDefinition egg(String id, String displayName, int health, String nextFormId) {
        return MonsterDefinition
                .builder(id, displayName, health)
                .sprite("act1/" + id)
                .script(variant -> Scripts.single(Intents.hatch(1, nextFormId)))
                .tag("act1", "boss", "egg")
                .build();
    }

    /** 凯洛斯蛋的完整形态链，顺序即出场顺序。 */
    public static List<MonsterDefinition> kairosEggChain() {
        return List.of(KAIROS_EGG_1, KAIROS_EGG_2, KAIROS_EGG_3, KAIROS_EGG_4);
    }

    // ==================================================================
    // 遭遇（编队）
    // ==================================================================

    /** 第一层全部怪物，顺序与设计案一致。 */
    public static List<MonsterDefinition> allMonsters() {
        return List.of(
                GRUB, WRAITH, SKELETON, EXPLORER_MALE, EXPLORER_FEMALE, GIANT_REMAINS,
                KAIROS_EGG_1, KAIROS_EGG_2, KAIROS_EGG_3, KAIROS_EGG_4, KAIROS);
    }

    private static void registerMonsters() {
        MonsterCatalog.registerAll(allMonsters());
    }

    private static void registerEncounters() {
        // 普通战斗池：每场随机抽一组
        EncounterCatalog.register(new EncounterDefinition(
                "act1_grubs", EncounterCategory.NORMAL, ACT,
                List.of(new MonsterSpawn("grub", 0), new MonsterSpawn("grub", 1)),
                "两条蛆，意图交错进行（一只打6、一只上虚弱）"));

        EncounterCatalog.register(new EncounterDefinition(
                "act1_wraith", EncounterCategory.NORMAL, ACT,
                List.of(MonsterSpawn.of("wraith")),
                "亡灵：开局先压 99 层易伤（待确认数值）"));

        EncounterCatalog.register(new EncounterDefinition(
                "act1_skeleton", EncounterCategory.NORMAL, ACT,
                List.of(MonsterSpawn.of("skeleton")),
                "骷髅：骨质疏松，被打得更疼，但自己也在掉血"));

        EncounterCatalog.register(new EncounterDefinition(
                "act1_explorers", EncounterCategory.NORMAL, ACT,
                List.of(new MonsterSpawn("explorer_male", 0), new MonsterSpawn("explorer_female", 0)),
                "探险者二人组：男的上甲、女的加力量与回血，需要决定先杀谁"));

        // 精英池
        EncounterCatalog.register(new EncounterDefinition(
                "act1_giant_remains", EncounterCategory.ELITE, ACT,
                List.of(MonsterSpawn.of("giant_remains")),
                "巨人遗骸：血量跌破 20 后进入复苏阶段，行动判定两次"));

        // Boss
        EncounterCatalog.register(new EncounterDefinition(
                "act1_kairos", EncounterCategory.BOSS, ACT,
                List.of(MonsterSpawn.of("kairos_egg_1")),
                "凯洛斯：无暇 → 轻微破裂 → 中度破裂 → 几乎破裂 → 破壳而出，"
                        + "每次破裂给凯洛斯 +1 层蜕变（力量）"));
    }

    /** 第一层普通战斗的遭遇池。 */
    public static List<EncounterDefinition> normalEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.NORMAL, ACT);
    }

    /** 第一层精英战斗的遭遇池。 */
    public static List<EncounterDefinition> eliteEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.ELITE, ACT);
    }

    /** 第一层 Boss 遭遇。 */
    public static List<EncounterDefinition> bossEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.BOSS, ACT);
    }
}
