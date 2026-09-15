package com.roguelike.dungeon.game.enemy.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.intent.Intent;
import com.roguelike.dungeon.game.enemy.status.StatusEffect;
import com.roguelike.dungeon.game.enemy.status.StatusRegistry;

/**
 * 分阶段脚本：血量跌到某个阈值时切换意图表，并可附带状态的增减。
 *
 * <p>精英怪「巨人遗骸」就是它的标准用法：</p>
 * <pre>{@code
 * Scripts.phase(Scripts.loop(打6+易伤, 打12))                       // 第一阶段
 *        .addPhase(PhaseScript.Phase.of(
 *                "巨人遗骸 的血量跌落 20，无灵状态消失，获得状态「复苏」",
 *                self -> self.getHealth() <= 20,                     // 触发条件
 *                Scripts.loopFrom(1, 回复10血, 打6+易伤, 打12),        // 第二阶段意图表
 *                List.of(new RevivalStatus(1)),                     // 获得的
 *                List.of(StatusIds.SOULLESS)))                      // 失去的
 * }</pre>
 *
 * <p>阶段检查发生在每次 {@link #nextIntent}（也就是每回合开始前），
 * 因此血量变化会在<b>下一个回合</b>生效——这与「玩家打完一套 → 怪物换阶段」的节奏一致。</p>
 */
public final class PhaseScript implements EnemyScript {

    /**
     * 一个阶段的定义。
     *
     * @param announce     进入该阶段时打出的战斗日志，可为 {@code null}
     * @param enterWhen    触发条件
     * @param script       该阶段的意图脚本
     * @param addOnEnter   进入时获得的状态（可为空列表）
     * @param removeOnEnter 进入时移除的状态 ID（可为空列表）
     */
    public record Phase(String announce,
                        Predicate<Monster> enterWhen,
                        EnemyScript script,
                        List<StatusEffect> addOnEnter,
                        List<String> removeOnEnter) {

        public Phase {
            Objects.requireNonNull(enterWhen, "enterWhen");
            Objects.requireNonNull(script, "script");
            addOnEnter = addOnEnter == null ? List.of() : List.copyOf(addOnEnter);
            removeOnEnter = removeOnEnter == null ? List.of() : List.copyOf(removeOnEnter);
        }

        /** 只换意图表，不动状态的简易构造。 */
        public static Phase of(String announce, Predicate<Monster> enterWhen, EnemyScript script) {
            return new Phase(announce, enterWhen, script, List.of(), List.of());
        }

        /** 换意图表并移除若干状态的构造。 */
        public static Phase of(String announce, Predicate<Monster> enterWhen, EnemyScript script,
                               List<String> removeOnEnter) {
            return new Phase(announce, enterWhen, script, List.of(), removeOnEnter);
        }

        /** 完整构造：换意图表 + 获得状态 + 移除状态。 */
        public static Phase of(String announce, Predicate<Monster> enterWhen, EnemyScript script,
                               List<StatusEffect> addOnEnter, List<String> removeOnEnter) {
            return new Phase(announce, enterWhen, script, addOnEnter, removeOnEnter);
        }
    }

    /** 血量跌到该值（含）以下时进入下一阶段——巨人遗骸用到的常用条件。 */
    public static Predicate<Monster> healthAtMost(int threshold) {
        return self -> self.getHealth() <= threshold;
    }

    /** 血量百分比跌到该比例以下时进入下一阶段。 */
    public static Predicate<Monster> healthRatioBelow(double ratio) {
        return self -> self.getHealth() <= self.getMaxHealth() * ratio;
    }

    /** 身上带有某个状态时进入下一阶段。 */
    public static Predicate<Monster> hasStatus(String statusId) {
        return self -> self.hasStatus(statusId);
    }

    /** 死亡时（一般不用，胜负由战斗模块判定）。 */
    public static Predicate<Monster> dead() {
        return Monster::isDead;
    }

    private final EnemyScript initialScript;
    private final List<Phase> laterPhases = new ArrayList<>();
    private final Set<Integer> enteredPhases = new HashSet<>();
    private EnemyScript activeScript;

    /**
     * @param initialScript 第一阶段（血量还健康时）使用的脚本
     */
    public PhaseScript(EnemyScript initialScript) {
        this.initialScript = Objects.requireNonNull(initialScript, "initialScript");
        this.activeScript = initialScript;
    }

    /** 追加一个后续阶段，按添加顺序检查，先满足条件的先生效。 */
    public PhaseScript addPhase(Phase phase) {
        laterPhases.add(Objects.requireNonNull(phase, "phase"));
        return this;
    }

    @Override
    public Intent nextIntent(Monster self, BattleContext ctx) {
        for (int i = 0; i < laterPhases.size(); i++) {
            if (enteredPhases.contains(i)) {
                continue;
            }
            Phase phase = laterPhases.get(i);
            if (!phase.enterWhen().test(self)) {
                continue;
            }
            enteredPhases.add(i);
            enterPhase(self, ctx, phase);
            activeScript = phase.script();
        }
        return activeScript.nextIntent(self, ctx);
    }

    /** 当前生效的脚本。 */
    public EnemyScript activeScript() {
        return activeScript;
    }

    /** 第一阶段脚本。 */
    public EnemyScript initialScript() {
        return initialScript;
    }

    private void enterPhase(Monster self, BattleContext ctx, Phase phase) {
        if (phase.announce() != null) {
            ctx.log("【阶段变化】" + phase.announce());
        }
        for (String statusId : phase.removeOnEnter()) {
            if (self.hasStatus(statusId)) {
                self.removeStatus(statusId);
                ctx.log("  " + self.displayName() + " 失去了状态「"
                        + StatusRegistry.displayName(statusId) + "」");
            }
        }
        for (StatusEffect status : phase.addOnEnter()) {
            self.applyStatus(status);
            ctx.log("  " + self.displayName() + " 获得了状态「" + status.describe() + "」");
        }
    }
}
