package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.card.CardType;
import com.roguelike.dungeon.game.entity.FullHealMaxHpDownRelic;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 遗物目录与掉落池。
 *
 * <p>定义风格与 {@link com.roguelike.dungeon.game.card.CardLibrary} 一致：
 * 无状态遗物用 lambda 直接写在这里，一眼能看完整套效果；
 * 只有带跨回合状态的遗物（钙化壳 / 放血槽 / 不死鸟之羽）才单独成类。</p>
 *
 * <p><b>所有对外方法都返回新实例</b>：遗物实例上可能带有累计计数，
 * 如果复用同一个单例，状态会在多次开局之间串味。</p>
 */
public final class RelicLibrary {

    // ---------- 初始遗物编号 ----------

    public static final String ANCHOR = "anchor";
    public static final String HEARTSTONE = "heartstone";
    public static final String BANDAGE = "bandage";

    // ---------- 掉落池编号 ----------

    public static final String WHETSTONE = "whetstone";
    public static final String NUMBING_AGENT = "numbing_agent";
    public static final String THORN_ARMOR = "thorn_armor";
    public static final String LEDGER = "ledger";
    public static final String HAMMER = "hammer";
    public static final String KUNAI = "kunai";
    public static final String SHURIKEN = "shuriken";
    public static final String BLOOD_LETTING_VALVE = "blood_letting_valve";
    public static final String BLOOD_PRICE = "blood_price";
    public static final String CALCIFIED_SHELL = "calcified_shell";
    public static final String EXECUTIONER = "executioner";
    public static final String PHOENIX_FEATHER = "phoenix_feather";
    public static final String GLASS_CANNON = "glass_cannon";
    public static final String GREEDY_CUP = "greedy_cup";

    // ---------- 扩充：普通 ----------

    /** 铁誓：每回合开始获得护甲。 */
    public static final String IRON_WILL = "iron_will";
    /** 拾荒者：击杀时回血并叠甲。 */
    public static final String SCAVENGER = "scavenger";
    /** 沉稳之手：每回合第一张牌叠甲。 */
    public static final String STEADY_HANDS = "steady_hands";
    /** 惯性：每累计打出 5 张牌造成穿透伤害。 */
    public static final String MOMENTUM = "momentum";
    /** 回气：回合结束能量耗尽时叠甲。 */
    public static final String SECOND_WIND = "second_wind";

    // ---------- 扩充：罕见 ----------

    /** 毒华：每累计 3 张技能牌给怪物中毒。 */
    public static final String TOXIC_BLOOM = "toxic_bloom";
    /** 嗜血獠牙：以伤害换回血。 */
    public static final String BLOODTHIRSTY_FANG = "bloodthirsty_fang";
    /** 符印之墙：多出牌换护甲。 */
    public static final String WALL_OF_SIGILS = "wall_of_sigils";
    /** 过载核心：回合结束按出牌数造成穿透伤害。 */
    public static final String OVERLOAD_CORE = "overload_core";
    /** 掠食凶性：每累计 4 张攻击牌叠力量。 */
    public static final String PREDATORS_FEROCITY = "predators_ferocity";

    // ---------- 扩充：稀有 ----------

    /** 势不可挡：回合结束按当前护甲造成穿透伤害。 */
    public static final String JUGGERNAUT = "juggernaut";
    /** 收割灵魂：击杀永久提升最大生命。 */
    public static final String SOUL_HARVEST = "soul_harvest";
    /** 回响之室：每回合第一张牌免费。 */
    public static final String ECHO_CHAMBER = "echo_chamber";

    // ---------- 扩充：Boss ----------

    public static final String DARK_PACT = "dark_pact";
    public static final String CRIMSON_CROWN = "crimson_crown";
    public static final String LEVIATHAN_HEART = "leviathan_heart";
    public static final String AEGIS_OF_RUIN = "aegis_of_ruin";
    public static final String SOULBOUND_LEDGER = "soulbound_ledger";
    public static final String DOOMSDAY_CLOCK = "doomsday_clock";

    /**
     * 高塔之匙：剧情道具，<b>不参与</b> Boss 三选一随机池。
     *
     * <p>由流程层定点发放（Boss 常规奖励结算后额外给，见 {@code GameController}），
     * 本身没有任何战斗效果 —— 放进随机池只会给玩家一个「死选项」，
     * 所以它在 {@link #BOSS_POOL_EXCLUDED} 里被显式排除。</p>
     */
    public static final String TOWER_KEY = "tower_key";

    // ---------- 扩充：Boss（第二批，池子补齐到 12 件） ----------

    /** 铁律：重构费用结构，技能牌变便宜、攻击牌变贵。 */
    public static final String IRON_EDICT = "iron_edict";
    /** 双刃：全部减费，代价是承伤上升。 */
    public static final String TWIN_CURSE = "twin_curse";
    /** 猎首徽记：击杀滚力量，代价是每战开场定额掉血。 */
    public static final String PREDATORS_CREST = "predators_crest";
    /** 毒心：开战让全体中毒，代价是自己的直伤变低。 */
    public static final String VENOM_HEART = "venom_heart";
    /** 苦修誓约：以能量上限换高额直伤与稳定护甲。 */
    public static final String ASCETIC_VOW = "ascetic_vow";
    /** 嗜血纹章：开战同时获得力量与易伤。 */
    public static final String BLOODLUST_SIGIL = "bloodlust_sigil";

    /**
     * 每局固定获得的初始遗物。
     *
     * <p>前三件是纯增益，用来保证开局有稳定战力；第四件「血之代价」
     * 则是续航核心——它以「最大生命 -1」为代价，换来每场战斗胜利后回满生命，
     * 正好补上「跨战斗掉血不断累积」这个最容易让玩家卡关的缺口。</p>
     */
    private static final List<String> STARTING_IDS =
            List.of(ANCHOR, HEARTSTONE, BANDAGE, BLOOD_PRICE);

    private static final Map<String, Supplier<Relic>> FACTORIES = new LinkedHashMap<>();

    /**
     * BOSS 稀有度里<b>不进随机三选一</b>的遗物。
     *
     * <p>用「稀有度」和「是否可随机获得」两个维度分开表达：像「高塔之匙」这种
     * 剧情 / 钥匙类道具，稀有度必须是 BOSS 才不会掉进普通与精英的掉落池，
     * 但它没有任何战斗效果，由流程层定点发放，因此不能出现在三选一里。</p>
     */
    private static final Set<String> BOSS_POOL_EXCLUDED = Set.of(TOWER_KEY);

    static {
        // ==================== 初始遗物 ====================

        register(ANCHOR, () -> new SimpleRelic(ANCHOR, "船锚",
                "战斗开始时获得 8 点护甲。", RelicRarity.COMMON,
                Set.of(RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    ctx.player().addArmor(8);
                    ctx.log("「船锚」触发：获得 8 点护甲。");
                }));

        register(HEARTSTONE, () -> new SimpleRelic(HEARTSTONE, "心之石",
                "每回合开始时给怪物 1 层易伤。", RelicRarity.COMMON,
                Set.of(RelicTrigger.TURN_START),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    ctx.battle().addMonsterStacks(StatusEffect.VULNERABLE, 1);
                    ctx.log("「心之石」触发：怪物获得 1 层易伤。");
                }));

        register(BANDAGE, () -> new SimpleRelic(BANDAGE, "绷带",
                "每场战斗胜利后回复 5 点生命。", RelicRarity.COMMON,
                Set.of(RelicTrigger.BATTLE_END),
                (trigger, ctx) -> {
                    int before = ctx.player().getHealth();
                    ctx.player().heal(5);
                    int healed = ctx.player().getHealth() - before;
                    if (healed > 0) {
                        ctx.log("「绷带」触发：回复 " + healed + " 点生命。");
                    }
                }));

        // 血之代价：初始遗物。稀有度标为「罕见」，但它开局即赠送，
        // 因此不会再从掉落池里抽到（randomReward 会跳过玩家已持有的遗物）。
        register(BLOOD_PRICE, FullHealMaxHpDownRelic::new);

        // ==================== 普通 ====================

        register(WHETSTONE, () -> new SimpleRelic(WHETSTONE, "磨刀石",
                "你造成的伤害 +1。", RelicRarity.COMMON,
                Set.of(RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> ctx.addValue(1)));

        register(NUMBING_AGENT, () -> new SimpleRelic(NUMBING_AGENT, "麻醉剂",
                "战斗开始时给全体怪物 2 层虚弱。", RelicRarity.COMMON,
                Set.of(RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    // 开战布局类效果作用于全体：多怪编队下只麻痹一只，
                    // 「削弱这伙敌人」的语义就不成立了。
                    ctx.battle().addAllMonsterStacks(StatusEffect.WEAK, 2);
                    ctx.log("「麻醉剂」触发：全体怪物获得 2 层虚弱。");
                }));

        register(THORN_ARMOR, () -> new SimpleRelic(THORN_ARMOR, "荆棘之甲",
                "每次受到伤害时，对怪物造成 3 点无视护甲的伤害。", RelicRarity.COMMON,
                Set.of(RelicTrigger.DAMAGE_TAKEN),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    int dealt = ctx.battle().dealDirectDamageToMonster(3);
                    if (dealt > 0) {
                        ctx.log("「荆棘之甲」触发：反弹 " + dealt + " 点伤害。");
                    }
                }));

        register(LEDGER, () -> new SimpleRelic(LEDGER, "记账本",
                "回合结束时若本回合打出了至少 4 张牌，回复 2 点生命。", RelicRarity.COMMON,
                Set.of(RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (ctx.battle() == null
                            || ctx.battle().cardsPlayedThisTurn() < 4) {
                        return;
                    }
                    int before = ctx.player().getHealth();
                    ctx.player().heal(2);
                    if (ctx.player().getHealth() > before) {
                        ctx.log("「记账本」触发：回复 2 点生命。");
                    }
                }));

        register(HAMMER, () -> new SimpleRelic(HAMMER, "重锤",
                "怪物处于防御姿态时，你造成的伤害 +4。", RelicRarity.COMMON,
                Set.of(RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    if (ctx.battle() != null && !ctx.battle().monsterWillAttack()) {
                        ctx.addValue(4);
                    }
                }));

        register(IRON_WILL, () -> new SimpleRelic(IRON_WILL, "铁誓",
                "每回合开始时获得 3 点护甲。", RelicRarity.COMMON,
                Set.of(RelicTrigger.TURN_START),
                (trigger, ctx) -> {
                    ctx.player().addArmor(3);
                    ctx.log("「铁誓」触发：获得 3 点护甲。");
                }));

        register(SCAVENGER, () -> new SimpleRelic(SCAVENGER, "拾荒者",
                "击杀怪物时回复 2 点生命并获得 5 点护甲。", RelicRarity.COMMON,
                Set.of(RelicTrigger.ENEMY_KILLED),
                (trigger, ctx) -> {
                    ctx.player().heal(2);
                    ctx.player().addArmor(5);
                    ctx.log("「拾荒者」触发：回复 2 点生命，获得 5 点护甲。");
                }));

        register(STEADY_HANDS, () -> new SimpleRelic(STEADY_HANDS, "沉稳之手",
                "每回合打出的第 1 张牌使你获得 2 点护甲。", RelicRarity.COMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (ctx.battle() == null
                            || ctx.battle().cardsPlayedThisTurn() != 1) {
                        return;
                    }
                    ctx.player().addArmor(2);
                    ctx.log("「沉稳之手」触发：获得 2 点护甲。");
                }));

        register(MOMENTUM, () -> new SimpleRelic(MOMENTUM, "惯性",
                "每累计打出 5 张牌，对怪物造成 3 点无视护甲的伤害。", RelicRarity.COMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (ctx.battle() == null
                            || ctx.battle().cardsPlayedThisBattle() % 5 != 0) {
                        return;
                    }
                    int dealt = ctx.battle().dealDirectDamageToMonster(3);
                    if (dealt > 0) {
                        ctx.log("「惯性」触发：造成 " + dealt + " 点无视护甲伤害。");
                    }
                }));

        register(SECOND_WIND, () -> new SimpleRelic(SECOND_WIND, "回气",
                "回合结束时若能量为 0，获得 3 点护甲。", RelicRarity.COMMON,
                Set.of(RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (ctx.player().getEnergy() != 0) {
                        return;
                    }
                    ctx.player().addArmor(3);
                    ctx.log("「回气」触发：获得 3 点护甲。");
                }));

        // ==================== 罕见 ====================

        register(KUNAI, () -> new SimpleRelic(KUNAI, "苦无",
                "每累计打出 3 张攻击牌，获得 2 点护甲。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (!isAttack(ctx) || ctx.battle() == null
                            || ctx.battle().attacksPlayedThisBattle() % 3 != 0) {
                        return;
                    }
                    ctx.player().addArmor(2);
                    ctx.log("「苦无」触发：获得 2 点护甲。");
                }));

        register(SHURIKEN, () -> new SimpleRelic(SHURIKEN, "手里剑",
                "每累计打出 3 张攻击牌，造成 3 点无视护甲的伤害。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (!isAttack(ctx) || ctx.battle() == null
                            || ctx.battle().attacksPlayedThisBattle() % 3 != 0) {
                        return;
                    }
                    int dealt = ctx.battle().dealDirectDamageToMonster(3);
                    if (dealt > 0) {
                        ctx.log("「手里剑」触发：造成 " + dealt + " 点无视护甲伤害。");
                    }
                }));

        register(TOXIC_BLOOM, () -> new SimpleRelic(TOXIC_BLOOM, "毒华",
                "每累计打出 3 张技能牌，给怪物 2 层中毒。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (ctx.battle() == null
                            || ctx.battle().skillsPlayedThisBattle() % 3 != 0) {
                        return;
                    }
                    ctx.battle().addMonsterStacks(StatusEffect.POISON, 2);
                    ctx.log("「毒华」触发：怪物获得 2 层中毒。");
                }));

        register(BLOODTHIRSTY_FANG, () -> new SimpleRelic(BLOODTHIRSTY_FANG, "嗜血獠牙",
                "你造成的伤害 -1；每次造成伤害时回复 1 点生命。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    ctx.addValue(-1);
                    int before = ctx.player().getHealth();
                    ctx.player().heal(1);
                    if (ctx.player().getHealth() > before) {
                        ctx.log("「嗜血獠牙」触发：回复 1 点生命。");
                    }
                }));

        register(WALL_OF_SIGILS, () -> new SimpleRelic(WALL_OF_SIGILS, "符印之墙",
                "回合结束时若本回合打出至少 3 张牌，获得 6 点护甲。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (ctx.battle() == null
                            || ctx.battle().cardsPlayedThisTurn() < 3) {
                        return;
                    }
                    ctx.player().addArmor(6);
                    ctx.log("「符印之墙」触发：获得 6 点护甲。");
                }));

        register(OVERLOAD_CORE, () -> new SimpleRelic(OVERLOAD_CORE, "过载核心",
                "回合结束时，造成等于本回合已打出牌数 ×2 的无视护甲伤害。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    int amount = ctx.battle().cardsPlayedThisTurn() * 2;
                    if (amount <= 0) {
                        return;
                    }
                    int dealt = ctx.battle().dealDirectDamageToMonster(amount);
                    if (dealt > 0) {
                        ctx.log("「过载核心」触发：造成 " + dealt + " 点无视护甲伤害。");
                    }
                }));

        register(PREDATORS_FEROCITY, () -> new SimpleRelic(PREDATORS_FEROCITY, "掠食凶性",
                "每累计打出 4 张攻击牌，获得 1 层力量。", RelicRarity.UNCOMMON,
                Set.of(RelicTrigger.CARD_PLAYED),
                (trigger, ctx) -> {
                    if (!isAttack(ctx) || ctx.battle() == null
                            || ctx.battle().attacksPlayedThisBattle() % 4 != 0) {
                        return;
                    }
                    ctx.player().addStacks(StatusEffect.STRENGTH, 1);
                    ctx.log("「掠食凶性」触发：获得 1 层力量。");
                }));

        register(BLOOD_LETTING_VALVE, BloodlettingValveRelic::new);

        // ==================== 稀有 ====================

        register(CALCIFIED_SHELL, CalcifiedShellRelic::new);

        register(EXECUTIONER, () -> new SimpleRelic(EXECUTIONER, "处刑者",
                "怪物生命低于 35% 时，你造成的伤害 +50%。", RelicRarity.RARE,
                Set.of(RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    int maxHp = ctx.battle().monsterMaxHp();
                    if (maxHp > 0 && ctx.battle().monsterHp() * 100 <= maxHp * 35) {
                        ctx.multiplyValue(1.5);
                    }
                }));

        register(PHOENIX_FEATHER, PhoenixFeatherRelic::new);

        register(GLASS_CANNON, () -> new SimpleRelic(GLASS_CANNON, "玻璃大炮",
                "你造成的伤害 +50%，但受到的伤害也 +50%。", RelicRarity.RARE,
                Set.of(RelicTrigger.DAMAGE_DEALT, RelicTrigger.DAMAGE_TAKEN),
                (trigger, ctx) -> ctx.multiplyValue(1.5)));

        // 代价从「最大生命 -8」加重到 -15：+1 能量是全游戏最强的单条收益，
        // 稀有档给到「-8」会严格优于 Boss 档的「黑暗契约（-12）」，稀有度曲线是反的。
        // 这里只调到刚好低于 Boss 档，不做更重的惩罚 —— 再重（如 -20）会让它变成
        // 没人敢拿的死选项，等于把「死内容」从一个位置挪到另一个位置。
        register(GREEDY_CUP, () -> new SimpleRelic(GREEDY_CUP, "贪婪之杯",
                "获得时：能量上限 +1，最大生命 -15。", RelicRarity.RARE,
                Set.of(RelicTrigger.OBTAIN),
                (trigger, ctx) -> {
                    ctx.player().reduceMaxHealth(15);
                    ctx.player().addMaxEnergy(1);
                    ctx.log("「贪婪之杯」触发：能量上限 +1，最大生命 -15。");
                }));

        register(JUGGERNAUT, () -> new SimpleRelic(JUGGERNAUT, "势不可挡",
                "回合结束时，造成等于你当前护甲 50% 的无视护甲伤害。", RelicRarity.RARE,
                Set.of(RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    int amount = ctx.player().getArmor() / 2;
                    if (amount <= 0) {
                        return;
                    }
                    int dealt = ctx.battle().dealDirectDamageToMonster(amount);
                    if (dealt > 0) {
                        ctx.log("「势不可挡」触发：造成 " + dealt + " 点无视护甲伤害。");
                    }
                }));

        register(SOUL_HARVEST, () -> new SimpleRelic(SOUL_HARVEST, "收割灵魂",
                "击杀怪物时，本局最大生命 +3（并回复等量生命）。", RelicRarity.RARE,
                Set.of(RelicTrigger.ENEMY_KILLED),
                (trigger, ctx) -> {
                    ctx.player().increaseMaxHealth(3);
                    ctx.log("「收割灵魂」触发：最大生命 +3。");
                }));

        register(ECHO_CHAMBER, EchoChamberRelic::new);

        // ==================== Boss ====================

        register(DARK_PACT, () -> new SimpleRelic(DARK_PACT, "黑暗契约",
                "获得时：能量上限 +1，最大生命 -12。", RelicRarity.BOSS,
                Set.of(RelicTrigger.OBTAIN),
                (trigger, ctx) -> {
                    ctx.player().reduceMaxHealth(12);
                    ctx.player().addMaxEnergy(1);
                    ctx.log("「黑暗契约」触发：能量上限 +1，最大生命 -12。");
                }));

        // 高塔之匙：剧情道具，定点发放，不进随机池。
        // 稀有度保持 BOSS 是为了不在普通 / 精英掉落里出现；能不能被抽到则交给
        // BOSS_POOL_EXCLUDED 决定 —— 两者是分开的两件事。
        register(TOWER_KEY, () -> new SimpleRelic(TOWER_KEY, "高塔之匙",
                "更深度探索的钥匙......", RelicRarity.BOSS,
                Set.of(),
                (trigger, ctx) -> {
                    // 占位遗物：目前没有战斗效果。
                }));

        // 从 +35% 上修到 +50%：Boss 档不能弱于同类的稀有「玻璃大炮」（+50%/+50%）。
        // 代价落在「每场战斗开始时失去 20% 当前生命」，是每战重来的一次性代价，
        // 与玻璃大炮「永久提高承伤」不同 —— 短战越猛，长线越危险。
        register(CRIMSON_CROWN, () -> new SimpleRelic(CRIMSON_CROWN, "猩红王冠",
                "你造成的伤害 +50%；每场战斗开始时失去 20% 当前生命。", RelicRarity.BOSS,
                Set.of(RelicTrigger.DAMAGE_DEALT, RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.DAMAGE_DEALT) {
                        ctx.multiplyValue(1.5);
                        return;
                    }
                    int before = ctx.player().getHealth();
                    int after = Math.max(1, before * 4 / 5);
                    ctx.player().setHealth(after);
                    if (before > after) {
                        ctx.log("「猩红王冠」代价：失去 " + (before - after) + " 点生命。");
                    }
                }));

        // 从 +30 上修到 +40：50 血玩家 +30 只有 60% 增幅，再扣掉每回合 -2 的持续侵蚀，
        // 净收益不足以支撑一件「Boss」档。+40 让「血上限极高但被 DoT 磨」的定位立得住。
        register(LEVIATHAN_HEART, () -> new SimpleRelic(LEVIATHAN_HEART, "利维坦之心",
                "获得时：最大生命 +40 并回满生命；每回合结束时失去 2 点生命。", RelicRarity.BOSS,
                Set.of(RelicTrigger.OBTAIN, RelicTrigger.TURN_END),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.OBTAIN) {
                        ctx.player().increaseMaxHealth(40);
                        ctx.player().healToFull();
                        ctx.log("「利维坦之心」触发：最大生命 +40 并回满生命。");
                        return;
                    }
                    int before = ctx.player().getHealth();
                    int after = Math.max(1, before - 2);
                    ctx.player().setHealth(after);
                    if (before > after) {
                        ctx.log("「利维坦之心」代价：失去 2 点生命。");
                    }
                }));

        register(AEGIS_OF_RUIN, () -> new SimpleRelic(AEGIS_OF_RUIN, "废墟之盾",
                "每回合开始时获得 6 点护甲；你造成的伤害 -20%。", RelicRarity.BOSS,
                Set.of(RelicTrigger.TURN_START, RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.TURN_START) {
                        ctx.player().addArmor(6);
                        ctx.log("「废墟之盾」触发：获得 6 点护甲。");
                        return;
                    }
                    ctx.multiplyValue(0.8);
                }));

        // 胜利收益从 +5 上修到 +8（净 +6/战）：原来的净 +3/战 打不过稀有的「收割灵魂」
        // （击杀最大生命 +3，且没有任何代价）。Boss 档必须反超稀有档。
        register(SOULBOUND_LEDGER, () -> new SimpleRelic(SOULBOUND_LEDGER, "缚魂账簿",
                "每场战斗胜利后最大生命 +8；每场战斗开始时最大生命 -2。", RelicRarity.BOSS,
                Set.of(RelicTrigger.BATTLE_START, RelicTrigger.BATTLE_END),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.BATTLE_START) {
                        ctx.player().reduceMaxHealth(2);
                        ctx.log("「缚魂账簿」代价：最大生命 -2。");
                        return;
                    }
                    ctx.player().increaseMaxHealth(8);
                    ctx.log("「缚魂账簿」触发：最大生命 +8。");
                }));

        // 原来 ×2 / ×0.6 的期望倍率 = (2 + 0.6 + 0.6) / 3 ≈ 1.067，也就是平均只 +6.7%，
        // 配不上 Boss 档（对比：稀有「玻璃大炮」是稳定 +50%）。
        // 调成 ×2.5 / ×0.85 后期望 = (2.5 + 0.85 + 0.85) / 3 ≈ 1.40，峰值更锋利，
        // 「把输出压缩到特定回合」的节奏感也更强。
        register(DOOMSDAY_CLOCK, () -> new SimpleRelic(DOOMSDAY_CLOCK, "末日之钟",
                "每第 3 个回合，你造成的伤害 ×2.5；其余回合 ×0.85。", RelicRarity.BOSS,
                Set.of(RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    if (ctx.battle().turnNumber() % 3 == 0) {
                        ctx.multiplyValue(2.5);
                        ctx.log("「末日之钟」触发：本回合伤害 ×2.5。");
                    } else {
                        ctx.multiplyValue(0.85);
                    }
                }));

        // ==================== Boss：第二批（池子补齐到 12 件） ====================
        //
        // 这 6 件在「代价货币」上与第一批刻意错开：
        // 费用结构 / 承伤 / 开场定额生命 / 毒流 / 能量 / 状态。
        // 目的是让三选一里任意两件都是「打法不同」，而不是「谁数值高」。

        // 用「费用重构」表达构筑取向：技能牌变便宜、攻击牌变贵。
        // 不新增触发点、不带状态 —— modifyCost 拿不到 RelicContext，但也正好不需要：
        // 判断只看牌本身，是纯粹的费用函数。无触发点意味着不占用触发点索引。
        register(IRON_EDICT, () -> new SimpleRelic(IRON_EDICT, "铁律",
                "你打出的技能牌费用 -1，攻击牌费用 +1（最低 0）。", RelicRarity.BOSS,
                Set.of(),
                (trigger, ctx) -> {
                },
                (instance, cost) -> switch (instance.card().type()) {
                    case ATTACK -> cost + 1;
                    case SKILL -> Math.max(0, cost - 1);
                    default -> cost;
                }));

        // 无差别减费，代价转移到承伤。注意 DAMAGE_TAKEN 拿到的是「易伤结算前」的原始伤害，
        // 所以穿这 25% 与易伤 +50% 是**叠乘**关系，最高会到 1.875 倍 —— 描述里写清楚了。
        register(TWIN_CURSE, () -> new SimpleRelic(TWIN_CURSE, "双刃",
                "你打出的所有牌费用 -1（最低 0）；你受到的伤害 +25%（与易伤叠乘）。",
                RelicRarity.BOSS,
                Set.of(RelicTrigger.DAMAGE_TAKEN),
                (trigger, ctx) -> ctx.multiplyValue(1.25),
                (instance, cost) -> cost - 1));

        // 代价用「定额」而不是「比例」：血多时便宜、血少时要命，
        // 与「猩红王冠」的 20% 当前生命正好构成两种风险口味。
        register(PREDATORS_CREST, () -> new SimpleRelic(PREDATORS_CREST, "猎首徽记",
                "击杀怪物时获得 2 层力量；每场战斗开始时失去 10 点生命。", RelicRarity.BOSS,
                Set.of(RelicTrigger.ENEMY_KILLED, RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.ENEMY_KILLED) {
                        ctx.player().addStacks(StatusEffect.STRENGTH, 2);
                        ctx.log("「猎首徽记」触发：获得 2 层力量。");
                        return;
                    }
                    int before = ctx.player().getHealth();
                    int after = Math.max(1, before - 10);
                    ctx.player().setHealth(after);
                    if (before > after) {
                        ctx.log("「猎首徽记」代价：失去 " + (before - after) + " 点生命。");
                    }
                }));

        // 毒不吃 DAMAGE_DEALT（中毒走 takeDamage），所以「伤害 -10%」几乎不伤毒的输出，
        // 只惩罚顺手平砍 —— 把构筑方向明确推向「毒是主输出」。
        // 开战类效果用 addAllMonsterStacks：只毒锁定目标的话，多怪场合这件遗物等于废掉。
        register(VENOM_HEART, () -> new SimpleRelic(VENOM_HEART, "毒心",
                "战斗开始时给全体怪物 5 层中毒；你造成的伤害 -10%。", RelicRarity.BOSS,
                Set.of(RelicTrigger.BATTLE_START, RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    if (trigger == RelicTrigger.DAMAGE_DEALT) {
                        ctx.multiplyValue(0.9);
                        return;
                    }
                    if (ctx.battle() == null) {
                        return;
                    }
                    ctx.battle().addAllMonsterStacks(StatusEffect.POISON, 5);
                    ctx.log("「毒心」触发：全体怪物获得 5 层中毒。");
                }));

        // 全池唯一一件「削减能量」的遗物：3 能量变 2 能量 ≈ 每回合少打一张牌，
        // 所以 +80% 伤害并不是白给。依赖本批次新增的 Player.reduceMaxEnergy。
        register(ASCETIC_VOW, () -> new SimpleRelic(ASCETIC_VOW, "苦修誓约",
                "获得时：能量上限 -1；你造成的伤害 +80%，每回合开始时获得 3 点护甲。",
                RelicRarity.BOSS,
                Set.of(RelicTrigger.OBTAIN, RelicTrigger.TURN_START,
                        RelicTrigger.DAMAGE_DEALT),
                (trigger, ctx) -> {
                    switch (trigger) {
                        case OBTAIN -> {
                            ctx.player().reduceMaxEnergy(1);
                            ctx.log("「苦修誓约」代价：能量上限 -1。");
                        }
                        case TURN_START -> {
                            ctx.player().addArmor(3);
                            ctx.log("「苦修誓约」触发：获得 3 点护甲。");
                        }
                        default -> ctx.multiplyValue(1.8);
                    }
                }));

        // 纯状态赌博：力量是整场的，易伤每回合末 -1 所以 4 层 = 前 4 回合承伤 +50%。
        // 危险窗口明确落在开局。BATTLE_START 在 resetForBattle 之后触发，这两层不会被清掉。
        register(BLOODLUST_SIGIL, () -> new SimpleRelic(BLOODLUST_SIGIL, "嗜血纹章",
                "战斗开始时获得 4 层力量与 4 层易伤。", RelicRarity.BOSS,
                Set.of(RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    ctx.player().addStacks(StatusEffect.STRENGTH, 4);
                    ctx.player().addStacks(StatusEffect.VULNERABLE, 4);
                    ctx.log("「嗜血纹章」触发：获得 4 层力量与 4 层易伤。");
                }));
    }

    private RelicLibrary() {
    }

    private static void register(String id, Supplier<Relic> factory) {
        FACTORIES.put(id, factory);
    }

    /** 判断本次打出的是否为攻击牌。 */
    private static boolean isAttack(RelicContext ctx) {
        return ctx.card() != null && ctx.card().card().type() == CardType.ATTACK;
    }

    /** 目录中全部遗物，每次调用都返回全新实例。 */
    public static List<Relic> all() {
        List<Relic> relics = new ArrayList<>(FACTORIES.size());
        for (Supplier<Relic> factory : FACTORIES.values()) {
            relics.add(factory.get());
        }
        return List.copyOf(relics);
    }

    /** 全部遗物编号，按定义顺序。 */
    public static List<String> allIds() {
        return List.copyOf(FACTORIES.keySet());
    }

    /** 每局开局固定获得的遗物。 */
    public static List<Relic> createStarting() {
        List<Relic> relics = new ArrayList<>(STARTING_IDS.size());
        for (String id : STARTING_IDS) {
            relics.add(create(id));
        }
        return List.copyOf(relics);
    }

    /** 初始遗物编号。 */
    public static List<String> startingIds() {
        return STARTING_IDS;
    }

    /**
     * 按编号创建一个全新遗物实例。
     *
     * @throws IllegalArgumentException 编号不存在
     */
    public static Relic create(String id) {
        Supplier<Relic> factory = FACTORIES.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("未知遗物编号：" + id);
        }
        return factory.get();
    }

    /** 按编号查询是否存在。 */
    public static boolean exists(String id) {
        return id != null && FACTORIES.containsKey(id);
    }

    /**
     * 随机抽取一个玩家尚未持有、且稀有度落在给定范围内的遗物。
     *
     * @param seed 随机种子，保证同一局面结果可复现
     * @param player 当前玩家，用于排除已持有的遗物
     * @param rarities 允许的稀有度；为空时表示不限
     * @return 抽到的遗物；全部已持有时返回空
     */
    public static Optional<Relic> randomReward(
            long seed,
            Player player,
            RelicRarity... rarities) {
        Set<RelicRarity> allowed = rarities == null || rarities.length == 0
                ? Set.of(RelicRarity.values())
                : Set.of(rarities);

        List<Relic> candidates = new ArrayList<>();
        for (Relic relic : all()) {
            if (player.hasRelicById(relic.id())) {
                continue;
            }
            if (allowed.contains(relic.rarity())) {
                candidates.add(relic);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(candidates, new Random(seed));
        return Optional.of(candidates.get(0));
    }

    /** 全部 Boss 遗物编号，按定义顺序。 */
    public static List<String> bossIds() {
        List<String> ids = new ArrayList<>();
        for (Relic relic : all()) {
            if (relic.rarity() == RelicRarity.BOSS) {
                ids.add(relic.id());
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Boss 遗物候选：随机取 {@code count} 件玩家尚未持有的 Boss 遗物，供玩家三选一。
     *
     * <p>与 {@link #randomReward} 的区别是这里要返回<b>多件互不重复</b>的遗物，
     * 且只从 {@link RelicRarity#BOSS} 里挑——Boss 遗物不进常规掉落池。</p>
     *
     * <p>{@link #BOSS_POOL_EXCLUDED} 里的剧情道具（「高塔之匙」）虽然稀有度是 BOSS，
     * 但没有战斗效果，不会出现在这里——否则玩家会抽到一个「死选项」。</p>
     *
     * <p>本方法只负责「选出候选」，实际获得仍需玩家通过
     * {@code RelicService.acquire} 领取；未被选中的候选不产生任何副作用。</p>
     *
     * @param seed   随机种子，保证同一局面结果可复现
     * @param player 当前玩家，用于排除已持有的 Boss 遗物
     * @param count  期望候选数量；不足时返回现有全部
     * @return 候选遗物列表，可能为空（Boss 遗物已全部持有）
     */
    public static List<Relic> bossChoices(long seed, Player player, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<Relic> candidates = new ArrayList<>();
        for (Relic relic : all()) {
            if (relic.rarity() == RelicRarity.BOSS
                    && !BOSS_POOL_EXCLUDED.contains(relic.id())
                    && !player.hasRelicById(relic.id())) {
                candidates.add(relic);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(candidates, new Random(seed));
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }
}
