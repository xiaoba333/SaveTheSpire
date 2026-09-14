package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Power;
import com.roguelike.dungeon.game.entity.StatusEffect;

import java.util.List;

/**
 * 把 {@link Combat} 当前状态映射为 http-api.md 定义的 BattleState JSON。
 *
 * <p>字段命名 camelCase，与前端 {@code SaveTheSpire.Models} 的字段一一对应。
 * 后端权威：这里只做「读 Combat 的当前快照 → 序列化」，不改变任何战斗状态。</p>
 */
public final class BattleStateJson {

    /** 后端 {@link Card} 目前没有稀有度字段，起始牌统一按 BASIC 处理（占位，后续接入真值）。 */
    private static final String DEFAULT_RARITY = "BASIC";

    private BattleStateJson() {
    }

    public static String toJson(String battleId, Combat combat, List<String> newLogs) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"battleId\":").append(Json.str(battleId)).append(',');
        sb.append("\"turnNumber\":").append(combat.getTurnNumber()).append(',');
        sb.append("\"phase\":").append(Json.str(combat.getPhase())).append(',');
        sb.append("\"player\":").append(playerJson(combat)).append(',');
        sb.append("\"enemies\":[").append(enemiesJson(combat)).append("],");
        sb.append("\"hand\":[").append(handJson(combat)).append("],");
        sb.append("\"piles\":").append(pilesJson(combat)).append(',');
        sb.append("\"result\":").append(Json.str(combat.getResult())).append(',');
        sb.append("\"newLogs\":").append(logsJson(newLogs));
        sb.append('}');
        return sb.toString();
    }

    private static String playerJson(Combat c) {
        return "{\"hp\":" + c.getPlayerHp()
                + ",\"maxHp\":" + c.getPlayerMaxHp()
                + ",\"armor\":" + c.getPlayerBlock()
                + ",\"energy\":" + c.getEnergy()
                + ",\"maxEnergy\":" + c.getPlayerMaxEnergy()
                + ",\"intent\":null"
                + ",\"buffs\":" + playerBuffsJson(c) + "}";
    }

    /**
     * 敌人数组：多怪编队时每个敌人一个元素，顺序与 {@code targetIndex} 一致。
     *
     * <p>单怪战斗仍是单元素数组，前端不需要分支处理。</p>
     */
    private static String enemiesJson(Combat c) {
        java.util.List<Combat.MonsterView> enemies = c.getEnemies();
        StringBuilder sb = new StringBuilder(enemies.size() * 96);
        for (int i = 0; i < enemies.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(enemyJson(c, enemies.get(i)));
        }
        return sb.toString();
    }

    private static String enemyJson(Combat c, Combat.MonsterView enemy) {
        String intentJson = enemy.intent() == null
                ? "null"
                : "{\"type\":" + Json.str(enemy.intent().type())
                        + ",\"value\":" + enemy.intent().value() + "}";
        return "{\"index\":" + enemy.index()
                + ",\"id\":" + Json.str(enemy.id())
                + ",\"name\":" + Json.str(enemy.name())
                + ",\"hp\":" + enemy.hp()
                + ",\"maxHp\":" + enemy.maxHp()
                + ",\"armor\":" + enemy.armor()
                + ",\"strength\":" + enemy.strength()
                + ",\"alive\":" + enemy.alive()
                + ",\"targeted\":" + enemy.targeted()
                + ",\"energy\":0"
                + ",\"maxEnergy\":0"
                + ",\"intent\":" + intentJson
                + ",\"buffs\":" + enemyBuffsJson(c, enemy.index()) + "}";
    }

    /** 玩家 buff = 状态效果（层数）+ 能力（无层数）。 */
    private static String playerBuffsJson(Combat c) {
        Player player = c.getPlayer();
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (StatusEffect effect : StatusEffect.values()) {
            int stacks = player.getStacks(effect);
            if (stacks <= 0) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(statusBuffJson(effect, stacks));
        }
        for (Power power : player.getPowers()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(powerBuffJson(power));
        }
        sb.append(']');
        return sb.toString();
    }

    /** 怪物 buff 只有状态效果（怪物无能力），按敌人下标逐个读取。 */
    private static String enemyBuffsJson(Combat c, int enemyIndex) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (StatusEffect effect : StatusEffect.values()) {
            int stacks = c.getMonsterStatusStacks(enemyIndex, effect);
            if (stacks <= 0) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(statusBuffJson(effect, stacks));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String statusBuffJson(StatusEffect effect, int stacks) {
        return "{\"id\":" + Json.str(effect.name())
                + ",\"name\":" + Json.str(effect.displayName())
                + ",\"description\":" + Json.str(effect.description())
                + ",\"stacks\":" + stacks
                + ",\"icon\":" + Json.str(iconKey(effect))
                + ",\"debuff\":" + isDebuff(effect) + "}";
    }

    private static String powerBuffJson(Power power) {
        return "{\"id\":" + Json.str(power.getClass().getSimpleName())
                + ",\"name\":" + Json.str(power.name())
                + ",\"description\":" + Json.str(power.description())
                + ",\"stacks\":0"
                + ",\"icon\":null"
                + ",\"debuff\":false}";
    }

    /** 图标键用枚举名小写，前端按 powers/48/{icon} 找图，找不到自动回退占位。 */
    private static String iconKey(StatusEffect effect) {
        return effect.name().toLowerCase();
    }

    private static boolean isDebuff(StatusEffect effect) {
        return switch (effect) {
            case VULNERABLE, WEAK, POISON -> true;
            default -> false;
        };
    }

    private static String handJson(Combat c) {
        return cardListInner(c.getHand());
    }

    /**
     * 「查看抽牌堆 / 弃牌堆」界面用的牌堆内容。
     *
     * <p>战斗状态里只带三个堆的<b>数量</b>（见 {@link #pilesJson}），因为每出一张牌都会
     * 拉一次战斗状态，把整堆牌都塞进去是白白的流量。内容只在玩家点开堆的时候单独取一次。</p>
     */
    public static String pilesDetailJson(Combat c) {
        return "{\"draw\":" + cardListJson(c.getDrawPileInstances())
                + ",\"discard\":" + cardListJson(c.getDiscardPileInstances())
                + "}";
    }

    /** 卡牌数组，自带方括号——给整体返回的接口用（如牌堆内容）。 */
    private static String cardListJson(List<CardInstance> cards) {
        return "[" + cardListInner(cards) + "]";
    }

    /**
     * 逗号分隔的卡牌列表内容，<b>不带方括号</b>，由调用方自己拼。
     * {@code toJson} 里的 hand / enemies 都是「调用方加括号」的写法，别在这里重复加。
     */
    private static String cardListInner(List<CardInstance> cards) {
        StringBuilder sb = new StringBuilder(cards.size() * 64);
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardJson(cards.get(i)));
        }
        return sb.toString();
    }

    private static String cardJson(CardInstance instance) {
        Card card = instance.card();
        return "{\"id\":" + Json.str(instance.id())
                + ",\"definitionId\":" + Json.str(card.id())
                + ",\"name\":" + Json.str(instance.displayName())
                + ",\"type\":" + Json.str(card.type().name())
                + ",\"cost\":" + card.cost()
                + ",\"effectiveCost\":" + instance.effectiveCost()
                + ",\"upgraded\":" + instance.upgraded()
                + ",\"upgradable\":" + card.upgradable()
                + ",\"description\":" + Json.str(instance.displayDescription())
                + ",\"exhausts\":" + card.exhausts()
                + ",\"playable\":" + card.playable()
                + ",\"rarity\":" + Json.str(card.rarity().name())
                + "}";
    }

    private static String pilesJson(Combat c) {
        return "{\"draw\":" + c.getDrawPileSize()
                + ",\"discard\":" + c.getDiscardPileSize()
                + ",\"exhaust\":" + c.getExhaustPileSize()
                + "}";
    }

    private static String logsJson(List<String> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Json.str(logs.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }
}
