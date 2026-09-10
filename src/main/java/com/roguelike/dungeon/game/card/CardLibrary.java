package com.roguelike.dungeon.game.card;

import com.roguelike.dungeon.game.entity.MetallicizePower;

import java.util.List;
import java.util.Map;

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
            "造成 6 点伤害。",
            context -> context.dealDamageToMonster(6),
            false,
            true,
            true);

    public static final Card DEFEND = new Card(
            "defend",
            "防御",
            CardType.SKILL,
            1,
            "获得 5 点护甲。",
            context -> context.addPlayerBlock(5),
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
                context.addPlayerEnergy(2);
            },
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

    // ---------- 血之代价角色卡（显式升级效果：升级只改数值、不降费用） ----------

    public static final Card BLOOD_ATTACK = new Card(
            "blood_attack",
            "攻击",
            CardType.ATTACK,
            1,
            "造成 6 点伤害。",
            context -> context.dealDamageToMonster(6),
            false,
            true,
            true,
            context -> context.dealDamageToMonster(9));

    public static final Card BLOOD_DEFEND = new Card(
            "blood_defend",
            "防御",
            CardType.SKILL,
            1,
            "获得 6 点护甲。",
            context -> context.addPlayerBlock(6),
            false,
            true,
            true,
            context -> context.addPlayerBlock(9));

    public static final Card BLOOD_FEAST = new Card(
            "blood_feast",
            "狂宴",
            CardType.ATTACK,
            1,
            "造成 6 点伤害。若击杀，最大生命 +1。",
            context -> {
                context.dealDamageToMonster(6);
                if (context.getMonsterHealth() <= 0) {
                    context.gainMaxHealth(1);
                }
            },
            false,
            true,
            true,
            context -> {
                context.dealDamageToMonster(9);
                if (context.getMonsterHealth() <= 0) {
                    context.gainMaxHealth(2);
                }
            });

    public static final Card BLOOD_DEVOTION_STRIKE = new Card(
            "blood_devotion",
            "献身打击",
            CardType.ATTACK,
            1,
            "造成等于当前生命值的伤害，失去 3 点生命。",
            context -> {
                context.dealDamageToMonster(context.getPlayerHealth());
                context.losePlayerHp(3);
            },
            false,
            true,
            true,
            context -> {
                context.dealDamageToMonster(context.getPlayerHealth());
                context.losePlayerHp(1);
            });

    /** 能力牌示例：打出后每回合开始获得护甲（消耗，但永久牌组保留，下局可再打）。 */
    public static final Card BLOOD_METALLICIZE = new Card(
            "blood_metallicize",
            "金属化",
            CardType.POWER,
            1,
            "每回合开始获得 3 点护甲。",
            context -> context.gainPower(new MetallicizePower(3)),
            true,
            true,
            true,
            context -> context.gainPower(new MetallicizePower(5)));

    private static final Map<String, Card> CARDS = Map.ofEntries(
            Map.entry(STRIKE.id(), STRIKE),
            Map.entry(DEFEND.id(), DEFEND),
            Map.entry(BASH.id(), BASH),
            Map.entry(QUICK_SLASH.id(), QUICK_SLASH),
            Map.entry(HEAVY_STRIKE.id(), HEAVY_STRIKE),
            Map.entry(IRON_WAVE.id(), IRON_WAVE),
            Map.entry(SHRUG_IT_OFF.id(), SHRUG_IT_OFF),
            Map.entry(BLOODLETTING.id(), BLOODLETTING),
            Map.entry(FORGE.id(), FORGE),
            Map.entry(BLOOD_ATTACK.id(), BLOOD_ATTACK),
            Map.entry(BLOOD_DEFEND.id(), BLOOD_DEFEND),
            Map.entry(BLOOD_FEAST.id(), BLOOD_FEAST),
            Map.entry(BLOOD_DEVOTION_STRIKE.id(), BLOOD_DEVOTION_STRIKE),
            Map.entry(BLOOD_METALLICIZE.id(), BLOOD_METALLICIZE));

    private CardLibrary() {
    }

    /** 创建基础起始牌组：5 张打击 + 5 张防御 + 1 张锻造。 */
    public static List<Card> startingDeck() {
        return List.of(
                STRIKE, STRIKE, STRIKE, STRIKE, STRIKE,
                DEFEND, DEFEND, DEFEND, DEFEND, DEFEND,
                FORGE);
    }

    /** 按 id 查询卡牌定义。 */
    public static Card byId(String id) {
        Card card = CARDS.get(id);
        if (card == null) {
            throw new IllegalArgumentException("未知卡牌 id：" + id);
        }
        return card;
    }
}
