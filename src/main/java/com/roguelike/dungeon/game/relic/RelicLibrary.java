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
    public static final String DARK_PACT = "dark_pact";

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
                "战斗开始时给怪物 2 层虚弱。", RelicRarity.COMMON,
                Set.of(RelicTrigger.BATTLE_START),
                (trigger, ctx) -> {
                    if (ctx.battle() == null) {
                        return;
                    }
                    ctx.battle().addMonsterStacks(StatusEffect.WEAK, 2);
                    ctx.log("「麻醉剂」触发：怪物获得 2 层虚弱。");
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

        register(GREEDY_CUP, () -> new SimpleRelic(GREEDY_CUP, "贪婪之杯",
                "获得时：能量上限 +1，最大生命 -8。", RelicRarity.RARE,
                Set.of(RelicTrigger.OBTAIN),
                (trigger, ctx) -> {
                    ctx.player().reduceMaxHealth(8);
                    ctx.player().addMaxEnergy(1);
                    ctx.log("「贪婪之杯」触发：能量上限 +1，最大生命 -8。");
                }));

        // ==================== Boss ====================

        register(DARK_PACT, () -> new SimpleRelic(DARK_PACT, "黑暗契约",
                "获得时：能量上限 +1，最大生命 -12。", RelicRarity.BOSS,
                Set.of(RelicTrigger.OBTAIN),
                (trigger, ctx) -> {
                    ctx.player().reduceMaxHealth(12);
                    ctx.player().addMaxEnergy(1);
                    ctx.log("「黑暗契约」触发：能量上限 +1，最大生命 -12。");
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
}
