package com.roguelike.dungeon.game.enemy.intent;

import java.util.Objects;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.MonsterCatalog;
import com.roguelike.dungeon.game.enemy.MonsterDefinition;
import com.roguelike.dungeon.game.enemy.status.StatusIds;
import com.roguelike.dungeon.game.enemy.status.StatusRegistry;

/**
 * 意图工厂：本框架的「领域语言」。
 *
 * <p>写怪物数据时只允许使用这里的方法，写法基本等于照着设计案念一遍：</p>
 * <pre>{@code
 * Intents.attack(6)                                    // 打6
 * Intents.attack(2, 3)                                 // 打2*3（2伤害3次）
 * Intents.attackWithStatus(6, StatusIds.VULNERABLE, 1) // 打6，给予玩家一层易伤
 * Intents.blockAndStrength(6, 2)                       // 防6并获得2力量
 * Intents.debuffPlayer(StatusIds.VULNERABLE, 99)       // 给予玩家99层易伤
 * Intents.giveAllyBlock("explorer_female", 10)         // 给女探险者上10甲
 * Intents.healAllies(6)                                // 给两人回6血
 * Intents.buffAlliesStrength(1)                        // 两人都加一力量
 * Intents.addCardToPlayerDrawPile("scale_shard", "甲片")
 * Intents.hatch(1, "kairos_egg_2")                     // 破裂（杀死自身），获得一层蜕变
 * Intents.healSelf(6)                                  // 回6
 * }</pre>
 *
 * <p>所有行动都已内建战斗日志，方便联调时直接看 {@code BattleContext#log} 的输出。</p>
 */
public final class Intents {

    /** 攻击意图的默认图标键。 */
    public static final String ICON_ATTACK = "intent_attack";
    /** 多段攻击意图的图标键。 */
    public static final String ICON_ATTACK_MULTI = "intent_attack_multi";
    /** 攻击 + 削弱意图的图标键。 */
    public static final String ICON_ATTACK_DEBUFF = "intent_attack_debuff";
    /** 防御意图的图标键。 */
    public static final String ICON_DEFEND = "intent_defend";
    /** 防御 + 强化意图的图标键。 */
    public static final String ICON_DEFEND_BUFF = "intent_defend_buff";
    /** 强化意图的图标键。 */
    public static final String ICON_BUFF = "intent_buff";
    /** 削弱意图的图标键。 */
    public static final String ICON_DEBUFF = "intent_debuff";
    /** 回复意图的图标键。 */
    public static final String ICON_HEAL = "intent_heal";
    /** 特殊意图的图标键。 */
    public static final String ICON_SPECIAL = "intent_special";

    private Intents() {
    }

    // ==================================================================
    // 攻击
    // ==================================================================

    /** 打 {@code damage} 点（实际伤害 = damage + 自身力量）。 */
    public static Intent attack(int damage) {
        return attack(damage, 1);
    }

    /**
     * 多段攻击：「打 2*3（2 伤害 3 次）」「打 0*9（0 伤害 9 次）」。
     *
     * <p>每一段都独立加上当前力量，因此凯洛斯的「打0*9」在 4 层蜕变下
     * 会变成 9 段各 4 点的伤害。</p>
     */
    public static Intent attack(int damage, int hits) {
        int count = Math.max(1, hits);
        String text = count == 1
                ? "打" + damage
                : "打" + damage + "*" + count + "（" + damage + "伤害" + count + "次）";
        String icon = count == 1 ? ICON_ATTACK : ICON_ATTACK_MULTI;
        return new Intent(IntentType.ATTACK, text, icon, (self, ctx) -> {
            for (int i = 0; i < count; i++) {
                if (self.isDead() || ctx.player().getHealth() <= 0) {
                    return;
                }
                int damageDealt = damage + self.getStrength();
                ctx.damagePlayer(damageDealt, self);
                ctx.log("  " + self.displayName() + " 攻击玩家 "
                        + damageDealt + " 点" + strengthSuffix(self));
            }
        });
    }

    /** 打 {@code damage} 点，并给玩家挂 {@code stacks} 层状态。 */
    public static Intent attackWithStatus(int damage, String statusId, int stacks) {
        String statusName = StatusRegistry.displayName(statusId);
        String text = "打" + damage + "，给予 " + stacks + " 层" + statusName;
        return new Intent(IntentType.ATTACK_DEBUFF, text, ICON_ATTACK_DEBUFF, (self, ctx) -> {
            int damageDealt = damage + self.getStrength();
            ctx.damagePlayer(damageDealt, self);
            ctx.log("  " + self.displayName() + " 攻击玩家 " + damageDealt + " 点" + strengthSuffix(self));
            ctx.applyStatusToPlayer(statusId, stacks, self);
            ctx.log("  " + self.displayName() + " 给予玩家 " + stacks + " 层" + statusName);
        });
    }

    // ==================================================================
    // 防御 / 强化
    // ==================================================================

    /** 自己叠 {@code amount} 点护甲。 */
    public static Intent block(int amount) {
        return new Intent(IntentType.DEFEND, "防" + amount, ICON_DEFEND, (self, ctx) -> {
            self.addArmor(amount);
            ctx.log("  " + self.displayName() + " 获得 " + amount + " 点护甲");
        });
    }

    /** 自己叠护甲并获得力量：「防6并获得2力量」。 */
    public static Intent blockAndStrength(int blockAmount, int strength) {
        String text = "防" + blockAmount + "并获得" + strength + "力量";
        return new Intent(IntentType.DEFEND_BUFF, text, ICON_DEFEND_BUFF, (self, ctx) -> {
            self.addArmor(blockAmount);
            self.addStrength(strength);
            ctx.log("  " + self.displayName() + " 获得 " + blockAmount + " 点护甲与 "
                    + strength + " 点力量（当前力量 " + self.getStrength() + "）");
        });
    }

    /** 给一名指定 ID 的存活同伴叠甲：「给女探险者上10甲」。 */
    public static Intent giveAllyBlock(String allyMonsterId, int blockAmount) {
        String allyName = allyDisplayName(allyMonsterId);
        String text = "给" + allyName + "上" + blockAmount + "甲";
        return new Intent(IntentType.DEFEND, text, ICON_DEFEND, (self, ctx) ->
                ctx.findAlly(self, allyMonsterId).ifPresentOrElse(ally -> {
                    ally.addArmor(blockAmount);
                    ctx.log("  " + self.displayName() + " 给 " + ally.displayName()
                            + " 上 " + blockAmount + " 点护甲");
                }, () -> ctx.log("  " + self.displayName() + " 想给 " + allyName
                        + " 上甲，但对方已经不在场上了")));
    }

    /** 自己和所有同伴各加 {@code strength} 点力量：「两人都加一力量」。 */
    public static Intent buffAlliesStrength(int strength) {
        String text = "全体加" + strength + "力量";
        return new Intent(IntentType.BUFF, text, ICON_BUFF, (self, ctx) -> {
            self.addStrength(strength);
            for (Monster ally : ctx.allies(self)) {
                ally.addStrength(strength);
            }
            ctx.log("  " + self.displayName() + " 让全体获得 " + strength + " 点力量");
        });
    }

    // ==================================================================
    // 削弱
    // ==================================================================

    /** 只给玩家挂状态，不造成伤害：「给予玩家99层易伤」。 */
    public static Intent debuffPlayer(String statusId, int stacks) {
        String statusName = StatusRegistry.displayName(statusId);
        String text = "给予玩家 " + stacks + " 层" + statusName;
        return new Intent(IntentType.DEBUFF, text, ICON_DEBUFF, (self, ctx) -> {
            ctx.applyStatusToPlayer(statusId, stacks, self);
            ctx.log("  " + self.displayName() + " 给予玩家 " + stacks + " 层" + statusName);
        });
    }

    // ==================================================================
    // 回复
    // ==================================================================

    /** 回复自己的血量：「回6」。 */
    public static Intent healSelf(int amount) {
        return new Intent(IntentType.HEAL, "回" + amount, ICON_HEAL, (self, ctx) -> {
            self.heal(amount);
            ctx.log("  " + self.displayName() + " 回复 " + amount + " 点生命（当前 "
                    + self.getHealth() + "/" + self.getMaxHealth() + "）");
        });
    }

    /** 自己和所有同伴各回血：「给两人回6血」。 */
    public static Intent healAllies(int amount) {
        String text = "全体回" + amount + "血";
        return new Intent(IntentType.HEAL, text, ICON_HEAL, (self, ctx) -> {
            self.heal(amount);
            for (Monster ally : ctx.allies(self)) {
                ally.heal(amount);
            }
            ctx.log("  " + self.displayName() + " 让全体回复 " + amount + " 点生命");
        });
    }

    // ==================================================================
    // 特殊
    // ==================================================================

    /** 向玩家抽牌堆塞牌（Boss 凯洛斯的「甲片」）。 */
    public static Intent addCardToPlayerDrawPile(String cardId, String cardDisplayName) {
        String text = "向玩家抽牌堆增加一张" + cardDisplayName;
        return new Intent(IntentType.SPECIAL, text, ICON_SPECIAL, (self, ctx) -> {
            ctx.addCardToPlayerDrawPile(cardId, 1);
            ctx.log("  " + self.displayName() + " 向玩家抽牌堆塞入 1 张「" + cardDisplayName + "」");
        });
    }

    /**
     * 破裂/孵化：杀死自身，并把 {@code metamorphosisPerHatch} 层「蜕变」带给下一个形态。
     *
     * <p>已累积的蜕变层数会被继承，因此「无暇 → 轻微破裂 → 中度破裂 → 几乎破裂 → 凯洛斯」
     * 这条链走完后，凯洛斯身上正好带着 4 层蜕变（力量 +4）。</p>
     *
     * @param metamorphosisPerHatch 本次破裂新增的蜕变层数
     * @param nextFormId            下一个形态的怪物定义 ID
     */
    public static Intent hatch(int metamorphosisPerHatch, String nextFormId) {
        String statusName = StatusRegistry.displayName(StatusIds.METAMORPHOSIS);
        String text = "破裂（杀死自身），获得 " + metamorphosisPerHatch + " 层" + statusName;
        return new Intent(IntentType.SPECIAL, text, ICON_SPECIAL, (self, ctx) -> {
            int carried = self.statusStacks(StatusIds.METAMORPHOSIS) + metamorphosisPerHatch;
            String eggName = self.displayName();
            MonsterDefinition nextForm = MonsterCatalog.require(nextFormId);
            self.setHealth(0);
            ctx.log("  " + eggName + " 破裂了！");
            Monster born = ctx.transform(self, nextForm);
            if (born != null) {
                if (carried > 0) {
                    born.applyStatus(StatusIds.METAMORPHOSIS, carried);
                }
                ctx.log("  " + born.displayName() + " 出现（" + born.getHealth() + "/"
                        + born.getMaxHealth() + "，力量 " + born.getStrength() + "）");
            }
        });
    }

    /** 自杀式撤离，例如蛋被提前打破但没有下一形态。 */
    public static Intent suicide(String reason) {
        return new Intent(IntentType.SPECIAL, reason, ICON_SPECIAL, (self, ctx) -> {
            self.setHealth(0);
            ctx.log("  " + self.displayName() + " " + reason);
        });
    }

    /** 什么都不做（占位 / 被眩晕跳过回合）。 */
    public static Intent idle() {
        return new Intent(IntentType.UNKNOWN, "什么都不做", ICON_SPECIAL, (self, ctx) ->
                ctx.log("  " + self.displayName() + " 按兵不动"));
    }

    // ==================================================================
    // 扩展出口
    // ==================================================================

    /**
     * 自定义意图——当已有 DSL 覆盖不到时用它，不要为了一个特例去改 {@link Intents}。
     *
     * @param type        意图类型
     * @param displayText 展示文本
     * @param iconKey     图标键
     * @param action      执行体
     */
    public static Intent custom(IntentType type, String displayText, String iconKey, EnemyAction action) {
        return new Intent(type, displayText, iconKey, action);
    }

    /** 把若干行动拼成一个意图，展示文本自定。 */
    public static Intent composite(IntentType type, String displayText, String iconKey, EnemyAction... actions) {
        Objects.requireNonNull(actions, "actions");
        return new Intent(type, displayText, iconKey, (self, ctx) -> {
            for (EnemyAction action : actions) {
                if (self.isDead()) {
                    return;
                }
                action.perform(self, ctx);
            }
        });
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private static String strengthSuffix(Monster self) {
        int strength = self.getStrength();
        return strength > 0 ? "（含力量 " + strength + "）" : "";
    }

    private static String allyDisplayName(String monsterId) {
        return MonsterCatalog.find(monsterId)
                .map(MonsterDefinition::displayName)
                .orElse(monsterId);
    }
}
