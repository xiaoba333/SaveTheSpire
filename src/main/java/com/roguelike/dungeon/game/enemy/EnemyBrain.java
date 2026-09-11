package com.roguelike.dungeon.game.enemy;

import java.util.Objects;

import com.roguelike.dungeon.game.enemy.intent.Intent;
import com.roguelike.dungeon.game.enemy.script.EnemyScript;

/**
 * 怪物大脑：把「决策」（{@link EnemyScript}）和「执行」（{@link Intent}）串起来的小运行时。
 *
 * <p>一场战斗里每个怪物各持有一个实例，标准回合节奏是：</p>
 * <pre>
 * 战斗开始 / 上回合结束后   →  {@link #plan}   生成下回合意图（UI 立刻显示在怪物头顶）
 * 怪物回合                  →  {@link #execute} 执行该意图
 * </pre>
 *
 * <p><b>为什么要把「定意图」和「执行意图」分开：</b>杀戮尖塔类游戏里，玩家需要在自己回合
 * 看到怪物「下回合要干什么」才能规划出牌。所以意图必须在怪物真正动手之前就确定好，
 * 而且确定之后不能再变（除非策划显式要求）。</p>
 *
 * <p>执行阶段会自动处理「复苏」这类状态带来的行动次数翻倍：
 * {@link Monster#actionRepeatMultiplier()} 返回几，整条意图就跑几遍。</p>
 */
public final class EnemyBrain {

    private final EnemyScript script;
    private Intent plannedIntent;
    private int plannedTurns;

    /**
     * @param script 该怪物专属的决策脚本（每个实例一份，不要共用）
     */
    public EnemyBrain(EnemyScript script) {
        this.script = Objects.requireNonNull(script, "script");
    }

    /** 该怪物的决策脚本。 */
    public EnemyScript script() {
        return script;
    }

    /**
     * 生成下一回合的意图并缓存下来。
     *
     * @return 本回合意图
     */
    public Intent plan(Monster self, BattleContext ctx) {
        plannedIntent = Objects.requireNonNull(script.nextIntent(self, ctx), "决策脚本返回了 null 意图");
        plannedTurns++;
        return plannedIntent;
    }

    /** 当前已确定、等待执行的意图；未规划时为 {@code null}。 */
    public Intent plannedIntent() {
        return plannedIntent;
    }

    /** 已经规划过的回合数。 */
    public int plannedTurns() {
        return plannedTurns;
    }

    /**
     * 执行当前缓存的意图。若尚未规划，会先补一次 {@link #plan}。
     */
    public void execute(Monster self, BattleContext ctx) {
        if (plannedIntent == null) {
            plan(self, ctx);
        }
        Intent intent = plannedIntent;
        int times = self.actionRepeatMultiplier();
        if (times > 1) {
            ctx.log("  " + self.displayName() + " 处于「复苏」，本回合行动判定 " + times + " 次");
        }
        for (int i = 0; i < times; i++) {
            if (self.isDead() || ctx.player().getHealth() <= 0) {
                break;
            }
            intent.action().perform(self, ctx);
            self.notifyActed(ctx);
        }
        // 执行掉之后清空，避免 UI 继续显示已经发生过的事
        plannedIntent = null;
    }
}
