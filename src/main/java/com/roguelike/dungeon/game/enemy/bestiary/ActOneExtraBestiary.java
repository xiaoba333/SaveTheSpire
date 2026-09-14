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
import com.roguelike.dungeon.game.enemy.intent.IntentType;
import com.roguelike.dungeon.game.enemy.script.PhaseScript;
import com.roguelike.dungeon.game.enemy.script.Scripts;
import com.roguelike.dungeon.game.enemy.status.RevivalStatus;
import com.roguelike.dungeon.game.enemy.status.StatusIds;

/**
 * 第一层「地牢外围」扩充图鉴：在 {@link ActOneBestiary} 之外补充的怪物与编队。
 *
 * <p>单独成类的理由是<b>不与他人改动冲突</b>：原有 11 只怪仍由 {@code ActOneBestiary}
 * 维护，本类只做加法，注册进同一套 {@code MonsterCatalog} / {@code EncounterCatalog}。
 * 若确认无冲突，两批数据也可以随时并回 {@code ActOneBestiary}。</p>
 *
 * <h3>这批怪物补的是什么</h3>
 * <p>原有 11 只怪的覆盖面是「直伤 + 叠甲 + 回血 + 加力量」，缺少三种压力：
 * <b>持续掉血（中毒）</b>、<b>高频小段伤害</b>、<b>污染玩家牌组</b>。本类的设计目标就是补上这三块，
 * 并给第一层补第二个 Boss 选项。</p>
 *
 * <h3>数据来源</h3>
 * <pre>
 * 普通怪
 *   菌生蹒跚者   24 血, 循环：打5并给玩家 1 层中毒 / 防8
 *   疫鼠         14 血, 初始「蠕动」, 循环：打3*2 / 给玩家 1 层虚弱
 *   铁壳虫       26 血, 循环：防10并给玩家 1 层虚弱 / 打7
 * 精英怪
 *   镜像幽魂     60 血, 循环：向玩家抽牌堆塞「虚幻之影」/ 打7*2 / 防10
 *   石哨兵       90 血, 阶段1 循环：打8+1易伤 / 防12并获得3力量
 *                       阶段2（血量 ≤ 45）外壳碎裂，放弃防御全力进攻
 *  Boss
 *   骸骨暴君    150 血, 初始「骨质疏松」, 阶段1 循环：打10 / 打16+2易伤 / 防15并获得3力量
 *                       阶段2（血量 ≤ 75）骨质疏松消失并获得「复苏」，行动判定两次
 * </pre>
 *
 * <h3>限制说明（重要）</h3>
 * <p>当前实战链路是 1v1：{@code ScriptedMonsterAi} 的 {@code BattleContext.allMonsters()}
 * 只返回自己、{@code allies()} 恒为空。因此本类<b>只定义单只出场的遭遇</b>，
 * 多人编队（原本设计里的「三只疫鼠」）要等多怪物战斗支持落地后再加，
 * 否则 {@code giveAllyBlock} / {@code buffAlliesStrength} 一类意图会静默失效。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ActOneExtraBestiary.init();                 // 幂等，重复调用安全
 * List<Monster> monsters = EncounterCatalog
 *         .require("act1x_stone_sentinel")
 *         .createMonsters();
 * }</pre>
 */
public final class ActOneExtraBestiary {

    /** 本层编号，与 {@link ActOneBestiary#ACT} 相同。 */
    public static final int ACT = 1;

    /** 镜像幽魂塞进玩家抽牌堆的卡牌 ID（卡牌定义由卡牌负责人维护）。 */
    public static final String CARD_MIRROR_SHADE = "mirror_shade";

    /** 遭遇 ID 前缀，与原有 act1_ 系列区分，便于按前缀筛选与排查。 */
    private static final String PREFIX = "act1x_";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private ActOneExtraBestiary() {
    }

    // ==================================================================
    // 初始化
    // ==================================================================

    /**
     * 注册扩充怪物与遭遇。幂等，重复调用安全。
     *
     * <p>由 {@code game.battle.MonsterCatalog} 的静态初始化块调用，
     * 因此只要战斗模块被加载过，这批数据就一定已经在目录里。</p>
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        MonsterCatalog.registerAll(allMonsters());
        registerEncounters();
    }

    /** 是否已完成初始化（测试用）。 */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    /**
     * 强制重新加载，仅供测试与 Demo 反复运行。
     *
     * <p>{@code MonsterCatalog.register} 对重复 ID 会抛异常，因此这里必须先让
     * {@link ActOneBestiary#reload()} 清空目录并重新注册原有数据，再补上本类的数据。</p>
     */
    public static void reload() {
        ActOneBestiary.reload();
        INITIALIZED.set(false);
        init();
    }

    // ==================================================================
    // 怪物定义
    // ==================================================================

    // ------------------------------ 普通怪 ------------------------------

    /**
     * 菌生蹒跚者：24 血。第一层唯一会「持续掉血」的小怪。
     *
     * <p>意图：1-2 循环「打5并给玩家 1 层中毒 / 防8」。</p>
     *
     * <p>设计意图：中毒不看护甲，逼玩家在「叠甲硬吃」和「抢节奏速杀」之间做选择，
     * 也让「防御牌」不再是万能解。</p>
     */
    public static final MonsterDefinition FUNGAL_SHAMBLER = MonsterDefinition
            .builder("fungal_shambler", "菌生蹒跚者", 24)
            .sprite("act1/fungal_shambler")
            .script(variant -> Scripts.loop(
                    Intents.attackWithStatus(5, StatusIds.POISON, 1),
                    Intents.block(8)))
            .tag("act1", "normal", "plant")
            .build();

    /**
     * 疫鼠：14 血，初始拥有状态「蠕动」（首次受击减半，受击后消失）。
     *
     * <p>意图：1-2 循环「打3*2 / 给玩家 1 层虚弱」。</p>
     *
     * <p>设计意图：血量最低但出手段数最多，「蠕动」让开场那一刀打不痛它——
     * 玩家需要先用一张低费牌剥掉蠕动，再用大牌收割，而不是无脑甩最高伤害。</p>
     *
     * <p class="note">设计案的原始形态是「三只疫鼠同时出场」，但当前实战是 1v1，
     * 因此先落单只形态；多怪物支持到位后改回 3 只编队即可（数据无需改）。</p>
     */
    public static final MonsterDefinition PLAGUE_RAT = MonsterDefinition
            .builder("plague_rat", "疫鼠", 14)
            .sprite("act1/plague_rat")
            .status(StatusIds.CREEPING, 1)
            .script(variant -> Scripts.loop(
                    Intents.attack(3, 2),
                    Intents.debuffPlayer(StatusIds.WEAK, 1)))
            .tag("act1", "normal", "vermin")
            .build();

    /**
     * 铁壳虫：26 血。第一层唯一「先削你再打」的防御型小怪。
     *
     * <p>意图：1-2 循环「防10并给玩家 1 层虚弱 / 打7」。</p>
     *
     * <p>设计意图：它的护甲会挡掉玩家的爆发，同时虚弱把玩家的输出压到 75%。
     * 玩家如果不在它叠甲前打掉护甲，很容易被拖到中毒/疲劳区。</p>
     *
     * <p>这里用 {@link Intents#composite} 组合「叠甲 + 削弱」，因为 DSL 里
     * 现成的 {@code blockAndStrength} 只覆盖「叠甲 + 强化自己」。</p>
     */
    public static final MonsterDefinition IRON_HUSK = MonsterDefinition
            .builder("iron_husk", "铁壳虫", 26)
            .sprite("act1/iron_husk")
            .script(variant -> Scripts.loop(
                    Intents.composite(IntentType.DEFEND_BUFF,
                            "防10，给予玩家 1 层虚弱",
                            Intents.ICON_DEFEND,
                            10,   // 图标上的数字：格挡量
                            Intents.block(10).action(),
                            Intents.debuffPlayer(StatusIds.WEAK, 1).action()),
                    Intents.attack(7)))
            .tag("act1", "normal", "insect")
            .build();

    // ------------------------------ 精英怪 ------------------------------

    /**
     * 镜像幽魂：60 血。第一层唯一会「污染玩家牌组」的精英。
     *
     * <p>意图：1-2-3 循环「向玩家抽牌堆塞 1 张「虚幻之影」/ 打7*2 / 防10」。</p>
     *
     * <p>设计意图：塞进抽牌堆的废牌会持续稀释玩家的手牌质量，越拖越难打——
     * 相当于给这场战斗加了一个「时间上限」。两段攻击（打7*2）则是为了惩罚
     * 「只叠一层薄甲」的打法：单次护甲收益被吃两遍。</p>
     *
     * <p class="note">依赖卡牌侧的 {@code mirror_shade} 定义；未实装时
     * {@code ScriptedMonsterAi} 会记一行日志并跳过，不会崩。</p>
     */
    public static final MonsterDefinition MIRROR_WRAITH = MonsterDefinition
            .builder("mirror_wraith", "镜像幽魂", 60)
            .sprite("act1/mirror_wraith")
            .script(variant -> Scripts.loop(
                    Intents.addCardToPlayerDrawPile(CARD_MIRROR_SHADE, "虚幻之影"),
                    Intents.attack(7, 2),
                    Intents.block(10)))
            .tag("act1", "elite", "spirit")
            .build();

    /**
     * 石哨兵：90 血，无初始状态。第一层唯一的「先守后攻」型精英。
     *
     * <p>阶段 1（血量 &gt; 45）：1-2 循环「打8并给予 1 层易伤 / 防12并获得 3 力量」。</p>
     * <p>阶段 2（血量 ≤ 45）：外壳碎裂，彻底放弃防御，2-3 循环
     * 「打14并给予 2 层易伤 / 打9并给予 3 层中毒 / 打20」。</p>
     *
     * <p>设计意图：与「巨人遗骸」按相反节奏设计——巨人遗骸是残血后变强并回血（鼓励一口气打穿），
     * 石哨兵则是残血后<b>不再叠甲但伤害暴涨</b>（鼓励留好防御资源再进残血阶段）。
     * 两只精英的应对方式互不通用，避免精英战变成同一种打法。</p>
     */
    public static final MonsterDefinition STONE_SENTINEL = MonsterDefinition
            .builder("stone_sentinel", "石哨兵", 90)
            .sprite("act1/stone_sentinel")
            .script(variant -> Scripts
                    .phase(
                            Intents.attackWithStatus(8, StatusIds.VULNERABLE, 1),
                            Intents.blockAndStrength(12, 3))
                    .addPhase(PhaseScript.Phase.of(
                            "石哨兵 的外壳碎裂，不再防御，全力进攻",
                            PhaseScript.healthAtMost(45),
                            Scripts.loopFrom(1,
                                    Intents.attackWithStatus(14, StatusIds.VULNERABLE, 2),
                                    Intents.attackWithStatus(9, StatusIds.POISON, 3),
                                    Intents.attack(20)))))
            .tag("act1", "elite", "construct")
            .build();

    // ------------------------------ Boss ------------------------------

    /**
     * 骸骨暴君：150 血，初始拥有「骨质疏松」。第一层的<b>第二个 Boss 选项</b>。
     *
     * <p>阶段 1（血量 &gt; 75）：1-2-3 循环「打10 / 打16并给予 2 层易伤 / 防15并获得 3 力量」。</p>
     * <p>阶段 2（血量 ≤ 75）：骨质疏松消失（腾出骨架的弱点），获得「复苏」（所有行动判定两次），
     * 2-3-4 循环「回15血 / 打10*3 / 打22并给予 3 层易伤 / 防20并获得 4 力量」。</p>
     *
     * <p>设计意图（与凯洛斯互补）：</p>
     * <ul>
     *   <li>凯洛斯考的是<b>节奏抉择</b>——早点打蛋还是晚点打蛋，代价在「蜕变」层数上；</li>
     *   <li>骸骨暴君考的是<b>资源管理</b>——骨质疏松让它每次行动自伤 2 点、且受到伤害 +2，
     *       所以「打得越快它越脆」；但把它打进残血后「复苏」会让它每回合行动两次，
     *       于是玩家必须先攒够护甲/爆发再进残血阶段，否则会被双倍行动直接带走。</li>
     * </ul>
     *
     * <p class="note">「复苏」对 Boss 是很重的强度，数值上把 150 血拆成
     * 「打 75 血 → 再面对双倍行动打 75 血」，实测偏难；如果测试下来过强，
     * 首选调参是把复苏阶段血量阈值从 75 下调到 50。</p>
     */
    public static final MonsterDefinition BONE_TYRANT = MonsterDefinition
            .builder("bone_tyrant", "骸骨暴君", 150)
            .sprite("act1/bone_tyrant")
            .status(StatusIds.OSTEOPOROSIS, 1)
            .script(variant -> Scripts
                    .phase(
                            Intents.attack(10),
                            Intents.attackWithStatus(16, StatusIds.VULNERABLE, 2),
                            Intents.blockAndStrength(15, 3))
                    .addPhase(PhaseScript.Phase.of(
                            "骸骨暴君 的骨架碎裂，骨质疏松消失，获得状态「复苏」",
                            PhaseScript.healthAtMost(75),
                            Scripts.loopFrom(1,
                                    Intents.healSelf(15),
                                    Intents.attack(10, 3),
                                    Intents.attackWithStatus(22, StatusIds.VULNERABLE, 3),
                                    Intents.blockAndStrength(20, 4)),
                            List.of(new RevivalStatus(1)),
                            List.of(StatusIds.OSTEOPOROSIS))))
            .tag("act1", "boss", "undead")
            .build();

    // ==================================================================
    // 遭遇（编队）
    // ==================================================================

    /** 本类新增的全部怪物，顺序与上文定义一致。 */
    public static List<MonsterDefinition> allMonsters() {
        return List.of(
                FUNGAL_SHAMBLER, PLAGUE_RAT, IRON_HUSK,
                MIRROR_WRAITH, STONE_SENTINEL, BONE_TYRANT);
    }

    /** 新增的普通怪 ID，供战斗模块扩充随机池。 */
    public static List<String> normalMonsterIds() {
        return List.of(FUNGAL_SHAMBLER.id(), PLAGUE_RAT.id(), IRON_HUSK.id());
    }

    /** 新增的精英怪 ID。 */
    public static List<String> eliteMonsterIds() {
        return List.of(MIRROR_WRAITH.id(), STONE_SENTINEL.id());
    }

    /** 新增的 Boss ID。 */
    public static List<String> bossMonsterIds() {
        return List.of(BONE_TYRANT.id());
    }

    private static void registerEncounters() {
        // 普通战斗池
        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "fungal_shambler", EncounterCategory.NORMAL, ACT,
                List.of(MonsterSpawn.of("fungal_shambler")),
                "菌生蹒跚者：边打边上毒，护甲挡不住持续掉血"));

        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "plague_rat", EncounterCategory.NORMAL, ACT,
                List.of(MonsterSpawn.of("plague_rat")),
                "疫鼠：血最少但段数最多，蠕动让开场那一刀打不痛它"));

        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "iron_husk", EncounterCategory.NORMAL, ACT,
                List.of(MonsterSpawn.of("iron_husk")),
                "铁壳虫：先叠甲再削你的输出，需要抢在它叠甲前破防"));

        // 精英池
        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "mirror_wraith", EncounterCategory.ELITE, ACT,
                List.of(MonsterSpawn.of("mirror_wraith")),
                "镜像幽魂：往抽牌堆塞废牌，越拖手牌越烂"));

        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "stone_sentinel", EncounterCategory.ELITE, ACT,
                List.of(MonsterSpawn.of("stone_sentinel")),
                "石哨兵：残血后放弃防御、伤害暴涨，进残血阶段前要留好防御"));

        // Boss 池
        EncounterCatalog.register(new EncounterDefinition(
                PREFIX + "bone_tyrant", EncounterCategory.BOSS, ACT,
                List.of(MonsterSpawn.of("bone_tyrant")),
                "骸骨暴君：骨质疏松让它越挨打越脆，但残血后「复苏」会让它每回合行动两次"));
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** 新增普通战斗的遭遇池。 */
    public static List<EncounterDefinition> normalEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.NORMAL, ACT).stream()
                .filter(e -> e.id().startsWith(PREFIX))
                .toList();
    }

    /** 新增精英战斗的遭遇池。 */
    public static List<EncounterDefinition> eliteEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.ELITE, ACT).stream()
                .filter(e -> e.id().startsWith(PREFIX))
                .toList();
    }

    /** 新增 Boss 遭遇。 */
    public static List<EncounterDefinition> bossEncounters() {
        return EncounterCatalog.byCategory(EncounterCategory.BOSS, ACT).stream()
                .filter(e -> e.id().startsWith(PREFIX))
                .toList();
    }

    /** 按 ID 取怪物定义，方便测试与 Demo 直接引用。 */
    public static MonsterDefinition require(String monsterId) {
        return MonsterCatalog.require(monsterId);
    }
}
