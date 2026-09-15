package com.roguelike.dungeon.game.enemy.status;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * 状态注册表：状态 ID → 创建器 的唯一映射。
 *
 * <p>怪物数据（{@code MonsterDefinition}）里只允许写状态 ID 字符串，
 * 由本表负责把 ID 还原成真正的 {@link StatusEffect} 实例。这样做的好处是
 * 数据与实现解耦：策划改数值不用碰代码，程序加状态不用改数据。</p>
 *
 * <p>新增状态的标准流程：① {@link StatusIds} 加常量；② 写 {@code StatusEffect} 子类；
 * ③ 在下面的静态块里 {@link #register}。</p>
 */
public final class StatusRegistry {

    private static final Map<String, IntFunction<StatusEffect>> FACTORIES = new LinkedHashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = new LinkedHashMap<>();

    static {
        register(StatusIds.CREEPING, "蠕动", CreepingStatus::new);
        register(StatusIds.OSTEOPOROSIS, "骨质疏松", OsteoporosisStatus::new);
        register(StatusIds.SOULLESS, "无灵", SoullessStatus::new);
        register(StatusIds.REVIVAL, "复苏", RevivalStatus::new);
        register(StatusIds.METAMORPHOSIS, "蜕变", MetamorphosisStatus::new);

        register(StatusIds.VULNERABLE, "易伤",
                stacks -> StackStatus.decaying(StatusIds.VULNERABLE, "易伤", stacks));
        register(StatusIds.WEAK, "虚弱",
                stacks -> StackStatus.decaying(StatusIds.WEAK, "虚弱", stacks));
        register(StatusIds.POISON, "中毒",
                stacks -> StackStatus.decaying(StatusIds.POISON, "中毒", stacks));
    }

    private StatusRegistry() {
    }

    /**
     * 注册一个状态。
     *
     * @param id          状态 ID
     * @param displayName 中文展示名
     * @param factory     层数 → 状态实例
     */
    public static void register(String id, String displayName, IntFunction<StatusEffect> factory) {
        FACTORIES.put(id, factory);
        DISPLAY_NAMES.put(id, displayName);
    }

    /**
     * 按 ID 创建状态实例。
     *
     * @throws IllegalArgumentException ID 未注册
     */
    public static StatusEffect create(String id, int stacks) {
        IntFunction<StatusEffect> factory = FACTORIES.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("未注册的状态 ID：" + id + "（请在 StatusRegistry 中注册）");
        }
        return factory.apply(stacks);
    }

    public static boolean isRegistered(String id) {
        return FACTORIES.containsKey(id);
    }

    /** 取状态的中文名；未注册时原样返回 ID，方便早早暴露问题。 */
    public static String displayName(String id) {
        return DISPLAY_NAMES.getOrDefault(id, id);
    }

    /** 已注册的全部状态 ID（只读，按注册顺序）。 */
    public static Map<String, String> registered() {
        return Collections.unmodifiableMap(DISPLAY_NAMES);
    }
}
