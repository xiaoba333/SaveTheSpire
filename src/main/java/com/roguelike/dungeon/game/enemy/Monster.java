package com.roguelike.dungeon.game.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.roguelike.dungeon.game.entity.Enemy;
import com.roguelike.dungeon.game.enemy.intent.Intent;
import com.roguelike.dungeon.game.enemy.status.StatusEffect;
import com.roguelike.dungeon.game.enemy.status.StatusRegistry;

/**
 * 怪物实体：在 {@link Enemy}（血量 + 护甲）之上补上 AI 需要的一切。
 *
 * <p>继承关系故意保持很浅：{@code Monster extends Enemy}，不引入新的属性层。
 * 血量、护甲、受击结算完全复用《实体接口规范》里已经对齐好的实现，
 * 本类只负责三件事：</p>
 *
 * <ol>
 *   <li><b>身份</b>：定义、立绘、标签、力量；</li>
 *   <li><b>状态</b>：持有 {@link StatusEffect} 列表并驱动它们的钩子；</li>
 *   <li><b>行为</b>：持有 {@link EnemyBrain}，对外暴露「规划意图 / 执行意图」。</li>
 * </ol>
 *
 * <p><b>与现有 {@code Enemy} 唯一的行为差异</b>：{@link #receiveDamage} 被重写为
 * 「先让状态修正伤害 → 再走护甲/血量结算 → 最后通知状态已受伤」的完整管道。
 * 因此所有伤害都必须走这个入口，不要直接调用 {@code takeDamage}——
 * 后者只在需要「无视护甲与修正的真实伤害」时使用（见 {@link #takeTrueDamage}）。</p>
 */
public class Monster extends Enemy {

    private final MonsterDefinition definition;
    private final int variant;
    private final List<StatusEffect> statuses = new ArrayList<>();
    private final EnemyBrain brain;
    private int baseStrength;

    /** 用定义生成一只怪物（同场同类无差异时用这个）。 */
    public Monster(MonsterDefinition definition) {
        this(definition, 0);
    }

    /**
     * 用定义生成一只怪物。
     *
     * @param variant 同场同类的序号（0 起），供「交错出手」等脚本差异使用
     */
    public Monster(MonsterDefinition definition, int variant) {
        super(definition.maxHealth());
        this.definition = Objects.requireNonNull(definition, "definition");
        this.variant = variant;
        this.brain = new EnemyBrain(definition.newScript(variant));
        // 初始状态是怪物自带配置，不经过「无灵」这类免疫判定
        for (StatusEffect status : definition.newStatuses()) {
            forceApplyStatus(status);
        }
    }

    // ==================================================================
    // 身份
    // ==================================================================

    /** 怪物定义 ID，例如 {@code "skeleton"}。 */
    public String id() {
        return definition.id();
    }

    /** 中文展示名，例如「骷髅」。 */
    public String displayName() {
        return definition.displayName();
    }

    /** 该怪物的定义。 */
    public MonsterDefinition definition() {
        return definition;
    }

    /** 同场同类的序号。 */
    public int variant() {
        return variant;
    }

    /** 分类标签。 */
    public Set<String> tags() {
        return definition.tags();
    }

    public boolean hasTag(String tag) {
        return definition.hasTag(tag);
    }

    /** 立绘资源路径（可能是尚未交付的路径）。 */
    public String spritePath() {
        return MonsterSprite.pathOf(definition);
    }

    /** 立绘资源路径，缺图时回落到兜底图。UI 请用这个。 */
    public String spriteOrDefault() {
        return MonsterSprite.pathOrDefault(definition);
    }

    // ==================================================================
    // 力量
    // ==================================================================

    /**
     * 当前力量 = 基础力量 + 所有状态的加成（「蜕变」每层 +1）。
     * 攻击意图用 {@code 基础伤害 + 力量} 计算最终伤害。
     */
    public int getStrength() {
        int total = baseStrength;
        for (StatusEffect status : statuses) {
            if (!status.isRemoved()) {
                total += status.strengthBonus();
            }
        }
        return total;
    }

    /** 不包含状态加成的基础力量。 */
    public int baseStrength() {
        return baseStrength;
    }

    /** 增加力量，例如「防6并获得2力量」里的 +2。 */
    public void addStrength(int amount) {
        if (amount > 0) {
            baseStrength += amount;
        }
    }

    /** 直接设定基础力量。 */
    public void setBaseStrength(int value) {
        this.baseStrength = Math.max(0, value);
    }

    /** 仅由状态提供的力量加成（用于 UI 区分「自身力量」与「蜕变加成」）。 */
    public int strengthFromStatuses() {
        return getStrength() - baseStrength;
    }

    // ==================================================================
    // 状态
    // ==================================================================

    /** 当前状态列表（只读）。 */
    public List<StatusEffect> statuses() {
        return Collections.unmodifiableList(statuses);
    }

    public boolean hasStatus(String statusId) {
        return statuses.stream().anyMatch(s -> !s.isRemoved() && s.id().equals(statusId));
    }

    public Optional<StatusEffect> findStatus(String statusId) {
        return statuses.stream().filter(s -> !s.isRemoved() && s.id().equals(statusId)).findFirst();
    }

    /** 某个状态的层数，没有则返回 0。 */
    public int statusStacks(String statusId) {
        return findStatus(statusId).map(StatusEffect::stacks).orElse(0);
    }

    /**
     * 给怪物挂状态。若身上存在免疫该状态的效果（如「无灵」免疫易伤/虚弱/中毒），
     * 会被静默拒绝并返回 {@code false}。
     *
     * @return 是否成功挂上
     */
    public boolean applyStatus(StatusEffect status) {
        Objects.requireNonNull(status, "status");
        if (status.stacks() <= 0) {
            return false;
        }
        if (blocksStatus(status.id())) {
            return false;
        }
        Optional<StatusEffect> existing = findStatus(status.id());
        if (existing.isPresent()) {
            existing.get().addStacks(status.stacks());
        } else {
            statuses.add(status);
        }
        return true;
    }

    /** 按 ID 挂状态。 */
    public boolean applyStatus(String statusId, int stacks) {
        return applyStatus(StatusRegistry.create(statusId, stacks));
    }

    /** 移除某个状态（无视免疫判定）。 */
    public void removeStatus(String statusId) {
        statuses.removeIf(s -> s.id().equals(statusId));
    }

    /** 是否有人免疫该状态。 */
    public boolean blocksStatus(String statusId) {
        for (StatusEffect status : statuses) {
            if (!status.isRemoved() && status.blocksStatus(statusId)) {
                return true;
            }
        }
        return false;
    }

    /** 所有状态合成一句展示文本，例如「骨质疏松 1 ｜ 复苏 1」；无状态时返回「—」。 */
    public String statusSummary() {
        List<String> parts = new ArrayList<>();
        for (StatusEffect status : statuses) {
            if (!status.isRemoved()) {
                parts.add(status.describe());
            }
        }
        return parts.isEmpty() ? "—" : String.join(" ｜ ", parts);
    }

    /**
     * 本回合行动次数倍率，由「复苏」这类状态决定（连乘）。
     */
    public int actionRepeatMultiplier() {
        int multiplier = 1;
        for (StatusEffect status : statuses) {
            if (!status.isRemoved()) {
                multiplier *= status.actionRepeatMultiplier();
            }
        }
        return Math.max(1, multiplier);
    }

    // ==================================================================
    // 伤害
    // ==================================================================

    /**
     * 标准受击入口（非攻击来源）。
     * 攻击类伤害请用 {@link #receiveDamage(DamageContext)}，以便「蠕动」能识别出来。
     */
    @Override
    public int receiveDamage(int damage) {
        return receiveDamage(new DamageContext(damage, this, false, null));
    }

    /**
     * 标准受击入口：状态修正 → 护甲/血量结算 → 受伤后钩子。
     *
     * @return 实际扣除的血量
     */
    public int receiveDamage(DamageContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (ctx.target() != this) {
            throw new IllegalArgumentException("DamageContext 的 target 不是当前怪物：" + ctx);
        }
        List<StatusEffect> snapshot = snapshot();

        // 1) 让状态修改即将承受的伤害（蠕动减半 / 骨质疏松 +2）
        for (StatusEffect status : snapshot) {
            if (!status.isRemoved()) {
                status.modifyIncomingDamage(ctx);
            }
        }

        // 2) 护甲先吸收，剩余由血量承担
        int actualLoss = super.receiveDamage(ctx.amount());
        ctx.setActualHealthLoss(actualLoss);

        // 3) 受伤后钩子（蠕动在这里消失）
        for (StatusEffect status : snapshot) {
            if (!status.isRemoved()) {
                status.onDamaged(ctx, this);
            }
        }
        removeMarkedStatuses();
        return actualLoss;
    }

    /**
     * 真实伤害：<b>无视护甲、也不经过任何状态修正</b>。
     *
     * <p>用于技能带来的自伤，例如「骨质疏松」的行动自伤 2 点——
     * 如果它走 {@link #receiveDamage}，会被自己那条「受到伤害额外 +2」重复放大。</p>
     *
     * @return 实际扣除的血量
     */
    public int takeTrueDamage(int amount) {
        if (amount <= 0) {
            return 0;
        }
        return takeDamage(amount);
    }

    // ==================================================================
    // 回合流程（由战斗模块按顺序调用）
    // ==================================================================

    /**
     * 怪物回合开始：清空上回合残留护甲，并驱动状态的回合开始钩子。
     * <p>注意：这里是《实体接口规范》5.3「回合刷新」在怪物侧的对应实现。</p>
     */
    public void onTurnStart(BattleContext ctx) {
        clearArmor();
        for (StatusEffect status : snapshot()) {
            if (!status.isRemoved()) {
                status.onTurnStart(this, ctx);
            }
        }
        removeMarkedStatuses();
    }

    /**
     * 规划下一回合的意图（战斗开始时、以及每次怪物行动结束后各调用一次）。
     *
     * @return 规划出的意图，可由 UI 直接显示在怪物头顶
     */
    public Intent planIntent(BattleContext ctx) {
        return brain.plan(this, ctx);
    }

    /** 当前已经确定、等待执行的意图；未规划时为 {@code null}。 */
    public Intent plannedIntent() {
        return brain.plannedIntent();
    }

    /** 当前意图的展示文本，未规划时返回空串。UI 友好。 */
    public String intentText() {
        Intent intent = brain.plannedIntent();
        return intent == null ? "" : intent.displayText();
    }

    /** 执行当前意图（会自动处理「复苏」带来的重复行动）。 */
    public void takeTurn(BattleContext ctx) {
        brain.execute(this, ctx);
    }

    /** 怪物回合结束：驱动状态钩子，并让会衰减的状态掉一层。 */
    public void onTurnEnd(BattleContext ctx) {
        for (StatusEffect status : snapshot()) {
            if (!status.isRemoved()) {
                status.onTurnEnd(this, ctx);
            }
        }
        removeMarkedStatuses();
        for (StatusEffect status : statuses) {
            if (!status.isRemoved() && status.decayEachTurn()) {
                status.addStacks(-1);
            }
        }
        removeMarkedStatuses();
    }

    /** 该怪物的决策脚本。 */
    public EnemyBrain brain() {
        return brain;
    }

    @Override
    public String toString() {
        return displayName() + "(" + id() + ") " + getHealth() + "/" + getMaxHealth()
                + " 护甲" + getArmor() + " 力量" + getStrength()
                + (statuses.isEmpty() ? "" : " [" + statusSummary() + "]");
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 由 {@link EnemyBrain} 在每执行完一次行动后调用，触发「每次行动都会受到2点伤害」这类效果。 */
    void notifyActed(BattleContext ctx) {
        for (StatusEffect status : snapshot()) {
            if (!status.isRemoved()) {
                status.onActed(this, ctx);
            }
        }
        removeMarkedStatuses();
    }

    /** 初始状态专用：跳过免疫判定，直接挂上。 */
    private void forceApplyStatus(StatusEffect status) {
        if (status.stacks() <= 0) {
            return;
        }
        statuses.add(status);
    }

    /** 遍历时使用快照，避免状态在钩子里增删自己导致 ConcurrentModificationException。 */
    private List<StatusEffect> snapshot() {
        return new ArrayList<>(statuses);
    }

    private void removeMarkedStatuses() {
        statuses.removeIf(StatusEffect::isRemoved);
    }
}
