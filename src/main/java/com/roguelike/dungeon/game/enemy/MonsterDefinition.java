package com.roguelike.dungeon.game.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import com.roguelike.dungeon.game.enemy.script.EnemyScript;
import com.roguelike.dungeon.game.enemy.status.StatusEffect;
import com.roguelike.dungeon.game.enemy.status.StatusRegistry;

/**
 * 怪物定义：一只怪的「设计案数据」，不可变，可被反复实例化。
 *
 * <p>它描述「是什么」，不描述「这一只现在怎么样」——血量、护甲、状态这些
 * 运行时状态在 {@link Monster} 里。因此同一个定义可以在同一场战斗里出现两次
 * （两条蛆），互不影响。</p>
 *
 * <p>典型写法（见 {@code ActOneBestiary}）：</p>
 * <pre>{@code
 * MonsterDefinition.builder("skeleton", "骷髅", 35)
 *         .sprite("act1/skeleton")
 *         .status(StatusIds.OSTEOPOROSIS, 1)
 *         .script(variant -> Scripts.loop(
 *                 Intents.attack(12),
 *                 Intents.healSelf(6)))
 *         .tag("act1", "normal")
 *         .build();
 * }</pre>
 */
public final class MonsterDefinition {

    private final String id;
    private final String displayName;
    private final int maxHealth;
    private final String spriteKey;
    private final List<StatusEffect> initialStatuses;
    private final IntFunction<EnemyScript> scriptFactory;
    private final Set<String> tags;

    private MonsterDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.maxHealth = builder.maxHealth;
        this.spriteKey = builder.spriteKey == null ? builder.id : builder.spriteKey;
        this.initialStatuses = List.copyOf(builder.initialStatuses);
        this.scriptFactory = builder.scriptFactory;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
    }

    // ------------------------------------------------------------------
    // 只读属性
    // ------------------------------------------------------------------

    /** 稳定唯一 ID，小写 snake_case，用于存档、遭遇表与立绘文件名。 */
    public String id() {
        return id;
    }

    /** 中文展示名，例如「凯洛斯的蛋（无暇）」。 */
    public String displayName() {
        return displayName;
    }

    /** 初始最大血量。 */
    public int maxHealth() {
        return maxHealth;
    }

    /** 立绘资源键，默认等于 {@link #id()}；实际路径见 {@link MonsterSprite}。 */
    public String spriteKey() {
        return spriteKey;
    }

    /** 分类标签，例如 act1 / normal / elite / boss / undead。 */
    public Set<String> tags() {
        return tags;
    }

    /** 是否带有某个标签。 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    // ------------------------------------------------------------------
    // 实例化
    // ------------------------------------------------------------------

    /**
     * 构造一份全新的初始状态列表（每次实例化都必须调用，不能共用同一批对象）。
     */
    public List<StatusEffect> newStatuses() {
        List<StatusEffect> copies = new ArrayList<>(initialStatuses.size());
        for (StatusEffect template : initialStatuses) {
            copies.add(StatusRegistry.create(template.id(), template.stacks()));
        }
        return copies;
    }

    /**
     * 构造一只怪物专属的脚本实例。
     *
     * @param variant 同一只怪在同场战斗中的第几只（0 起），用于「交错出手」这类差异；
     *                不需要区分的怪物忽略即可
     */
    public EnemyScript newScript(int variant) {
        return scriptFactory.apply(variant);
    }

    @Override
    public String toString() {
        return "MonsterDefinition{" + id + " / " + displayName + " hp=" + maxHealth + "}";
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder(String id, String displayName, int maxHealth) {
        return new Builder(id, displayName, maxHealth);
    }

    /** 怪物定义的链式构造器。 */
    public static final class Builder {

        private final String id;
        private final String displayName;
        private final int maxHealth;
        private String spriteKey;
        private final List<StatusEffect> initialStatuses = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();
        private IntFunction<EnemyScript> scriptFactory;

        private Builder(String id, String displayName, int maxHealth) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            if (maxHealth <= 0) {
                throw new IllegalArgumentException("怪物血量必须大于 0：" + id);
            }
            this.maxHealth = maxHealth;
        }

        /** 立绘资源键，例如 {@code "act1/skeleton"}。 */
        public Builder sprite(String spriteKey) {
            this.spriteKey = spriteKey;
            return this;
        }

        /** 增加一个初始状态。 */
        public Builder status(String statusId, int stacks) {
            this.initialStatuses.add(StatusRegistry.create(statusId, stacks));
            return this;
        }

        /** 增加一个已构造好的初始状态。 */
        public Builder status(StatusEffect status) {
            this.initialStatuses.add(Objects.requireNonNull(status, "status"));
            return this;
        }

        /** 配置决策脚本（每个实例一份，variant 为同场同类怪的序号）。 */
        public Builder script(IntFunction<EnemyScript> scriptFactory) {
            this.scriptFactory = Objects.requireNonNull(scriptFactory, "scriptFactory");
            return this;
        }

        /** 配置不含 variant 差异的决策脚本。 */
        public Builder script(java.util.function.Supplier<EnemyScript> scriptSupplier) {
            Objects.requireNonNull(scriptSupplier, "scriptSupplier");
            this.scriptFactory = variant -> scriptSupplier.get();
            return this;
        }

        /** 打标签，例如 act1 / normal / elite / boss。 */
        public Builder tag(String... tags) {
            this.tags.addAll(List.of(tags));
            return this;
        }

        public MonsterDefinition build() {
            return new MonsterDefinition(this);
        }
    }
}
