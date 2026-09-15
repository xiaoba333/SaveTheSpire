package com.roguelike.dungeon.game.enemy.status;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.DamageContext;
import com.roguelike.dungeon.game.enemy.Monster;

/**
 * 状态效果基类：一个状态 = 一份「层数」+ 一组可选的生命周期钩子。
 *
 * <p>设计思路和 {@code IHealth / IArmor / IEnergy} 一脉相承：怪物不硬编码任何特殊规则，
 * 状态自己收敛自己的行为。新增一个状态只需要新建一个子类、重写关心的钩子，
 * 再到 {@link StatusRegistry} 注册，不用改 {@link Monster}。</p>
 *
 * <p>钩子调用时机（全部由 {@link Monster} 驱动）：</p>
 * <pre>
 * 回合开始  onTurnStart
 * 受到伤害  modifyIncomingDamage → 实际结算 → onDamaged
 * 行动一次  onActed
 * 回合结束  onTurnEnd →（decayEachTurn 为 true 时层数 -1）
 * </pre>
 *
 * <p><b>层数语义约定：</b>子类用 {@link #stacks()} 决定效果强度，
 * 层数归零即自动标记为移除，{@link Monster} 会在安全时机回收。</p>
 */
public abstract class StatusEffect {

    private final String id;
    private final String displayName;
    private int stacks;
    private boolean removed;

    /**
     * @param id          状态 ID，取自 {@link StatusIds}
     * @param displayName 中文展示名，用于 UI 与战斗日志
     * @param stacks      初始层数，&lt;= 0 视为立即移除
     */
    protected StatusEffect(String id, String displayName, int stacks) {
        this.id = id;
        this.displayName = displayName;
        this.stacks = Math.max(0, stacks);
        this.removed = this.stacks <= 0;
    }

    // ------------------------------------------------------------------
    // 基础属性
    // ------------------------------------------------------------------

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final int stacks() {
        return stacks;
    }

    public final boolean isRemoved() {
        return removed;
    }

    /** 设置层数，归零时自动标记移除。 */
    public final void setStacks(int stacks) {
        this.stacks = Math.max(0, stacks);
        if (this.stacks == 0) {
            this.removed = true;
        }
    }

    /** 增减层数（可为负）。 */
    public final void addStacks(int delta) {
        setStacks(this.stacks + delta);
    }

    /** 立即标记为移除，{@link Monster} 会在本回合结束后回收。 */
    public final void markRemoved() {
        this.removed = true;
        this.stacks = 0;
    }

    /** "蠕动 1" / "易伤 99" 这样的展示文本。 */
    public final String describe() {
        return stacks > 1 ? displayName + " " + stacks : displayName;
    }

    // ------------------------------------------------------------------
    // 生命周期钩子（按需重写，默认什么都不做）
    // ------------------------------------------------------------------

    /** 怪物回合开始时（护甲清空之后、决定意图之前）调用。 */
    public void onTurnStart(Monster owner, BattleContext ctx) {
    }

    /**
     * 修改「即将承受的伤害」。
     * 只改 {@link DamageContext#amount()}，不要在这里做实际扣血。
     */
    public void modifyIncomingDamage(DamageContext ctx) {
    }

    /** 一次伤害结算完成之后调用（血量与护甲都已更新）。 */
    public void onDamaged(DamageContext ctx, Monster owner) {
    }

    /** 怪物每执行完一次行动后调用一次（「复苏」重复行动时会调用多次）。 */
    public void onActed(Monster owner, BattleContext ctx) {
    }

    /** 怪物回合结束时调用（在层数衰减之前）。 */
    public void onTurnEnd(Monster owner, BattleContext ctx) {
    }

    // ------------------------------------------------------------------
    // 被动查询
    // ------------------------------------------------------------------

    /** 是否免疫某个状态的施加（「无灵」免疫易伤 / 虚弱 / 中毒）。 */
    public boolean blocksStatus(String statusId) {
        return false;
    }

    /**
     * 怪物行动次数的倍率。默认 1；「复苏」返回 2，表示所有行动判定两次。
     * 多个状态同时存在时取连乘。
     */
    public int actionRepeatMultiplier() {
        return 1;
    }

    /** 对怪物「力量」的加成（「蜕变」每层 +1 力量）。 */
    public int strengthBonus() {
        return 0;
    }

    /**
     * 是否每回合结束自动减少 1 层。
     * 默认 {@code false}——与设计案中「蠕动不会随回合数减少」一致。
     */
    public boolean decayEachTurn() {
        return false;
    }

    @Override
    public String toString() {
        return describe();
    }
}
