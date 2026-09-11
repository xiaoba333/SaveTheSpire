package com.roguelike.dungeon.game.card;

import com.roguelike.dungeon.game.entity.MetallicizePower;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 当前版本卡牌定义与起始牌组。
 *
 * <p>这里用 Java 代码定义卡牌，后续如果需要做卡牌数据库、升级、稀有度，
 * 可以再抽成 JSON / 数据库配置。</p>
 */
public final class CardLibrary {

    public static final Card STRIKE = new Card(
            "strike",
            "打击",
            CardType.ATTACK,
            1,
            "造成 6 点伤害，升级后造成 9 点伤害。",
            "对一名敌人造成 9 点伤害。",
            context -> context.dealDamageToMonster(
                    context.isUpgraded() ? 9 : 6),
            false,
            true,
            true);

    public static final Card DEFEND = new Card(
            "defend",
            "防御",
            CardType.SKILL,
            1,
            "获得 6 点护甲，升级后获得 9 点护甲。",
            "获得 9 点护甲。",
            context -> context.addPlayerBlock(
                    context.isUpgraded() ? 9 : 6),
            false,
            true,
            true);

    public static final Card BASH = new Card(
            "bash",
            "痛击",
            CardType.ATTACK,
            2,
            "造成 8 点伤害。",
            context -> context.dealDamageToMonster(8),
            false,
            true,
            true);

    public static final Card QUICK_SLASH = new Card(
            "quick_slash",
            "快斩",
            CardType.ATTACK,
            0,
            "造成 3 点伤害。",
            context -> context.dealDamageToMonster(3),
            false,
            true,
            true);

    public static final Card HEAVY_STRIKE = new Card(
            "heavy_strike",
            "重击",
            CardType.ATTACK,
            2,
            "造成 12 点伤害。",
            context -> context.dealDamageToMonster(12),
            false,
            true,
            true);

    public static final Card IRON_WAVE = new Card(
            "iron_wave",
            "铁斩波",
            CardType.ATTACK,
            1,
            "造成 5 点伤害，获得 5 点护甲。",
            context -> {
                context.dealDamageToMonster(5);
                context.addPlayerBlock(5);
            },
            false,
            true,
            true);

    public static final Card SHRUG_IT_OFF = new Card(
            "shrug_it_off",
            "耸肩",
            CardType.SKILL,
            1,
            "获得 8 点护甲，抽 1 张牌。",
            context -> {
                context.addPlayerBlock(8);
                context.drawCards(1);
            },
            false,
            true,
            true);

    public static final Card BLOODLETTING = new Card(
            "bloodletting",
            "放血",
            CardType.SKILL,
            0,
            "失去 3 点生命，获得 2 点能量。",
            context -> {
                context.dealDamageToPlayer(3);
                context.addPlayerEnergy(context.isUpgraded() ? 3 : 2);
            },
            false,
            true,
            true);

    /**
     * 暴血：自伤 1，获得护甲。普通 9，升级 12。
     */
    public static final Card BLOOD_BURST = new Card(
            "blood_burst",
            "暴血",
            CardType.SKILL,
            1,
            "对自己造成 1 点伤害，获得 9 点护甲；升级后获得 12 点护甲。",
            "对自己造成 1 点伤害，获得 12 点护甲。",
            context -> {
                context.dealDamageToPlayer(1);
                context.addPlayerBlock(context.isUpgraded() ? 12 : 9);
            },
            false,
            true,
            true);

    /**
     * 鲜血领主：造成当前最大生命值伤害，并降低 1 点最大生命值。
     */
    public static final Card BLOOD_LORD = new Card(
            "blood_lord",
            "鲜血领主",
            CardType.ATTACK,
            1,
            "对一名敌人造成等于当前最大生命值的伤害，自己最大生命值减 1。",
            "对一名敌人造成等于当前最大生命值的伤害，自己最大生命值减 1。",
            context -> {
                context.dealDamageToMonster(context.getPlayerMaxHealth());
                context.reducePlayerMaxHealth(1);
            },
            false,
            true,
            true);

    /**
     * 血祭：降低最大生命值，获得能量并抽牌。
     */
    public static final Card BLOOD_SACRIFICE = new Card(
            "blood_sacrifice",
            "血祭",
            CardType.SKILL,
            0,
            "最大生命值减 1，能量加 2，抽 3 张牌；升级后能量加 3，抽 5 张牌。",
            "最大生命值减 1，能量加 3，抽 5 张牌。",
            context -> {
                context.reducePlayerMaxHealth(1);
                context.addPlayerEnergy(context.isUpgraded() ? 3 : 2);
                context.drawCards(context.isUpgraded() ? 5 : 3);
            },
            false,
            true,
            true);

    /**
     * 鲜血转换：自伤并抽牌。
     */
    public static final Card BLOOD_TRANSFUSION = new Card(
            "blood_transfusion",
            "鲜血转换",
            CardType.SKILL,
            1,
            "对自己造成 3 点伤害，抽 2 张牌；升级后抽 3 张牌。",
            "对自己造成 3 点伤害，抽 3 张牌。",
            context -> {
                context.dealDamageToPlayer(3);
                context.drawCards(context.isUpgraded() ? 3 : 2);
            },
            false,
            true,
            true);

    /**
     * 狂宴：造成伤害，若击杀敌人则提高最大生命值。
     *
     * <p>普通版：伤害 6，最大生命 +1；升级版：伤害 9，最大生命 +2。</p>
     */
    public static final Card FEAST = new Card(
            "feast",
            "狂宴",
            CardType.ATTACK,
            1,
            "造成 10 点伤害，若击杀敌人最大生命值 +1；升级后造成 13 点伤害，最大生命值 +2。",
            "对一名敌人造成 13 点伤害，若击杀敌人最大生命值 +2。",
            context -> {
                boolean killed = context.dealDamageToMonster(
                        context.isUpgraded() ? 13 : 10);
                if (killed) {
                    context.increasePlayerMaxHealth(
                            context.isUpgraded() ? 2 : 1);
                }
            },
            false,
            true,
            true);

    /**
     * 御血术：先对自己造成 2 点伤害，再对指定敌人造成伤害。
     *
     * <p>普通版：对敌人 12 点；升级版：对敌人 16 点。自伤始终为 2 点。</p>
     */
    public static final Card SACRIFICE_STRIKE = new Card(
            "sacrifice_strike",
            "御血术",
            CardType.ATTACK,
            1,
            "对自己造成 2 点伤害，对指定敌人造成 12 点伤害；升级后造成 16 点伤害。",
            "对自己造成 2 点伤害，对指定敌人造成 16 点伤害。",
            context -> {
                context.dealDamageToPlayer(2);
                context.dealDamageToMonster(context.isUpgraded() ? 16 : 12);
            },
            false,
            true,
            true);

    /**
     * 以下卡牌依赖尚未实现的战斗状态、死亡触发或多目标机制。
     * 当前只登记定义，效果暂时为空，不加入初始牌组或奖励池。
     */
    public static final Card BLOOD_HAPPINESS = new Card(
            "blood_happiness",
            "血之高兴",
            CardType.POWER,
            3,
            "当你减少自己血量上限时，改为血量上限 +1。",
            "当你减少自己血量上限时，改为血量上限 +1。",
            context -> { },
            false,
            true,
            true);

    public static final Card BLOOD_LACERATION = new Card(
            "blood_laceration",
            "鲜血淋漓",
            CardType.ATTACK,
            1,
            "对自己造成 3 点伤害，对指定敌人造成 10 点伤害并给予易伤。",
            "对自己造成 3 点伤害，对指定敌人造成 13 点伤害并给予 3 层易伤。",
            context -> {
                context.dealDamageToPlayer(3);
                context.dealDamageToMonster(context.isUpgraded() ? 13 : 10);
                context.applyStatusToMonster(
                        StatusEffect.VULNERABLE,
                        context.isUpgraded() ? 3 : 2);
            },
            false,
            true,
            true);

    public static final Card TEAR = new Card(
            "tear",
            "撕裂",
            CardType.POWER,
            1,
            "对自己造成伤害时，力量 +1；升级后力量 +2。",
            "对自己造成伤害时，力量 +2。",
            context -> { },
            false,
            true,
            true);

    public static final Card CRIMSON_POOL = new Card(
            "crimson_pool",
            "猩红之池",
            CardType.POWER,
            3,
            "对自己造成 3 点伤害，本回合免疫受到的伤害。",
            "对自己造成 3 点伤害，两回合内免疫受到的伤害。",
            context -> {
                context.dealDamageToPlayer(3);
                context.applyStatusToPlayer(
                        StatusEffect.BLOOD_POOL,
                        context.isUpgraded() ? 2 : 1);
            },
            false,
            true,
            true);

    public static final Card BLOOD_STRIP = new Card(
            "blood_strip",
            "血之剥离",
            CardType.ATTACK,
            1,
            "对指定敌人造成 6 点伤害，并给予 1 层虚弱。",
            "对指定敌人造成 9 点伤害，并给予 2 层虚弱。",
            context -> {
                context.dealDamageToMonster(context.isUpgraded() ? 9 : 6);
                context.applyStatusToMonster(
                        StatusEffect.WEAK,
                        context.isUpgraded() ? 2 : 1);
            },
            false,
            true,
            true);

    public static final Card VOMIT_BLOOD = new Card(
            "vomit_blood",
            "呕血",
            CardType.ATTACK,
            2,
            "对指定敌人造成 30 点伤害，给予自己 3 层虚弱。",
            "对指定敌人造成 40 点伤害，给予自己 3 层虚弱。",
            context -> {
                context.dealDamageToMonster(context.isUpgraded() ? 40 : 30);
                context.applyStatusToPlayer(StatusEffect.WEAK, 3);
            },
            false,
            true,
            true);

    public static final Card BLOOD_REBIRTH = new Card(
            "blood_rebirth",
            "鲜血重塑",
            CardType.POWER,
            4,
            "对指定对象附加状态「死而复生」。",
            "对指定对象附加状态「死而复生」。",
            context -> {
                context.applyStatusToPlayer(StatusEffect.REBORN, 1);
            },
            false,
            true,
            true);

    public static final Card BLOOD_RAIN = new Card(
            "blood_rain",
            "血雨",
            CardType.ATTACK,
            1,
            "对自己造成 1 点伤害，对所有敌人造成 6 点伤害，执行 x 次。",
            "对自己造成 1 点伤害，对所有敌人造成 9 点伤害，执行 x 次。",
            context -> { },
            false,
            true,
            true);

    public static final Card DUSK_VEIL = new Card(
            "dusk_veil",
            "暮色帷幕",
            CardType.SKILL,
            3,
            "给予所有敌人 99 层易伤，以及状态「血畜」。",
            "给予所有敌人 99 层易伤，99 层虚弱，以及状态「血畜」。",
            context -> { },
            false,
            true,
            true);

    /**
     * 锻造：升级玩家选中的一张手牌。
     *
     * <p>具体目标由前端通过 targetCardId 传入，最终注入到当前卡牌效果上下文。
     * 锻造牌本身不可升级，避免选择自己。</p>
     */
    public static final Card FORGE = new Card(
            "forge",
            "锻造",
            CardType.SKILL,
            1,
            "选择手牌中的一张牌并升级。",
            CardEffectContext::upgradeCard,
            false,
            true,
            false);

    // ---------- 血之代价角色卡 ----------

    public static final Card BLOOD_ATTACK = new Card(
            "blood_attack",
            "攻击",
            CardType.ATTACK,
            1,
            "造成 6 点伤害，升级后造成 9 点伤害。",
            "造成 9 点伤害。",
            context -> context.dealDamageToMonster(
                    context.isUpgraded() ? 9 : 6),
            false,
            true,
            true);

    public static final Card BLOOD_DEFEND = new Card(
            "blood_defend",
            "防御",
            CardType.SKILL,
            1,
            "获得 6 点护甲，升级后获得 9 点护甲。",
            "获得 9 点护甲。",
            context -> context.addPlayerBlock(
                    context.isUpgraded() ? 9 : 6),
            false,
            true,
            true);

    public static final Card BLOOD_FEAST = new Card(
            "blood_feast",
            "狂宴",
            CardType.ATTACK,
            1,
            "造成 6 点伤害，若击杀敌人最大生命值 +1；升级后造成 9 点伤害，最大生命值 +2。",
            "造成 9 点伤害，若击杀敌人最大生命值 +2。",
            context -> {
                boolean killed = context.dealDamageToMonster(
                        context.isUpgraded() ? 9 : 6);
                if (killed) {
                    context.increasePlayerMaxHealth(
                            context.isUpgraded() ? 2 : 1);
                }
            },
            false,
            true,
            true);

    public static final Card BLOOD_DEVOTION_STRIKE = new Card(
            "blood_devotion",
            "御血术",
            CardType.ATTACK,
            1,
            "对自己造成 2 点伤害，对指定敌人造成 12 点伤害；升级后造成 16 点伤害。",
            "对自己造成 2 点伤害，对指定敌人造成 16 点伤害。",
            context -> {
                context.dealDamageToPlayer(2);
                context.dealDamageToMonster(context.isUpgraded() ? 16 : 12);
            },
            false,
            true,
            true);

    /** 能力牌：打出后每回合开始获得护甲（消耗，但永久牌组保留，下局可再打）。 */
    public static final Card BLOOD_METALLICIZE = new Card(
            "blood_metallicize",
            "金属化",
            CardType.POWER,
            1,
            "每回合开始获得 3 点护甲，升级后获得 5 点护甲。",
            "每回合开始获得 5 点护甲。",
            context -> context.gainPower(new MetallicizePower(
                    context.isUpgraded() ? 5 : 3)),
            true,
            true,
            true);

    /** 测试用隐藏角色「god」的专属牌：1 费造成 999 点伤害。 */
    public static final Card DESCEND = new Card(
            "descend",
            "降神",
            CardType.ATTACK,
            1,
            "造成 999 点伤害。",
            context -> context.dealDamageToMonster(999),
            false,
            true,
            false);

    private static final Map<String, Card> CARDS = Map.ofEntries(
            Map.entry(STRIKE.id(), STRIKE),
            Map.entry(DEFEND.id(), DEFEND),
            Map.entry(BASH.id(), BASH),
            Map.entry(QUICK_SLASH.id(), QUICK_SLASH),
            Map.entry(HEAVY_STRIKE.id(), HEAVY_STRIKE),
            Map.entry(IRON_WAVE.id(), IRON_WAVE),
            Map.entry(SHRUG_IT_OFF.id(), SHRUG_IT_OFF),
            Map.entry(BLOODLETTING.id(), BLOODLETTING),
            Map.entry(BLOOD_BURST.id(), BLOOD_BURST),
            Map.entry(BLOOD_LORD.id(), BLOOD_LORD),
            Map.entry(BLOOD_SACRIFICE.id(), BLOOD_SACRIFICE),
            Map.entry(BLOOD_TRANSFUSION.id(), BLOOD_TRANSFUSION),
            Map.entry(FEAST.id(), FEAST),
            Map.entry(SACRIFICE_STRIKE.id(), SACRIFICE_STRIKE),
            Map.entry(BLOOD_HAPPINESS.id(), BLOOD_HAPPINESS),
            Map.entry(BLOOD_LACERATION.id(), BLOOD_LACERATION),
            Map.entry(TEAR.id(), TEAR),
            Map.entry(CRIMSON_POOL.id(), CRIMSON_POOL),
            Map.entry(BLOOD_STRIP.id(), BLOOD_STRIP),
            Map.entry(VOMIT_BLOOD.id(), VOMIT_BLOOD),
            Map.entry(BLOOD_REBIRTH.id(), BLOOD_REBIRTH),
            Map.entry(BLOOD_RAIN.id(), BLOOD_RAIN),
            Map.entry(DUSK_VEIL.id(), DUSK_VEIL),
            Map.entry(FORGE.id(), FORGE),
            Map.entry(BLOOD_ATTACK.id(), BLOOD_ATTACK),
            Map.entry(BLOOD_DEFEND.id(), BLOOD_DEFEND),
            Map.entry(BLOOD_FEAST.id(), BLOOD_FEAST),
            Map.entry(BLOOD_DEVOTION_STRIKE.id(), BLOOD_DEVOTION_STRIKE),
            Map.entry(BLOOD_METALLICIZE.id(), BLOOD_METALLICIZE),
            Map.entry(DESCEND.id(), DESCEND));

    private CardLibrary() {
    }

    /** 公共无色牌，所有角色的奖励/商店卡池都会并入。 */
    public static List<Card> colorlessRewardCards() {
        return List.of(FORGE);
    }

    /** 铁血战士专属奖励卡池（不含基础打击/防御，也不含公共无色牌）。 */
    public static List<Card> warriorRewardCards() {
        return List.of(BASH, QUICK_SLASH, HEAVY_STRIKE, IRON_WAVE, SHRUG_IT_OFF);
    }

    public static List<String> warriorRewardCardIds() {
        return ids(warriorRewardCards());
    }

    /**
     * 血之领主专属奖励卡池。
     *
     * <p>保留 {@link #FEAST} 和 {@link #BLOOD_DEVOTION_STRIKE}；
     * 不收录重复的 {@link #BLOOD_FEAST}、{@link #SACRIFICE_STRIKE}，
     * 也不收录效果尚未实现的牌。</p>
     */
    public static List<Card> bloodLordRewardCards() {
        return List.of(
                BLOODLETTING,
                BLOOD_BURST,
                BLOOD_LORD,
                BLOOD_SACRIFICE,
                BLOOD_TRANSFUSION,
                FEAST,
                BLOOD_DEVOTION_STRIKE,
                BLOOD_LACERATION,
                CRIMSON_POOL,
                BLOOD_STRIP,
                VOMIT_BLOOD,
                BLOOD_REBIRTH,
                BLOOD_METALLICIZE);
    }

    public static List<String> bloodLordRewardCardIds() {
        return ids(bloodLordRewardCards());
    }

    /** 角色专属卡池再加上公共无色牌，供战斗奖励和商店使用。 */
    public static List<Card> rewardPoolFor(List<String> characterRewardCardIds) {
        LinkedHashSet<Card> cards = new LinkedHashSet<>();
        for (String cardId : Objects.requireNonNull(characterRewardCardIds, "奖励卡池不能为 null")) {
            cards.add(byId(cardId));
        }
        cards.addAll(colorlessRewardCards());
        return List.copyOf(cards);
    }

    /** 创建铁血战士初始牌组：4 打击、4 防御、1 痛击。 */
    public static List<Card> startingDeck() {
        return List.of(
                STRIKE, STRIKE, STRIKE, STRIKE,
                DEFEND, DEFEND, DEFEND, DEFEND,
                BASH);
    }

    /** 按 id 查询卡牌定义。 */
    public static Card byId(String id) {
        Card card = CARDS.get(id);
        if (card == null) {
            throw new IllegalArgumentException("未知卡牌 id：" + id);
        }
        return card;
    }

    private static List<String> ids(List<Card> cards) {
        return cards.stream().map(Card::id).toList();
    }
}
