package com.roguelike.dungeon.game.enemy.encounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 遭遇登记处：战斗模块按「层数 + 节点类型」来抽取敌人的唯一入口。
 *
 * <p>用法（战斗模块里）：</p>
 * <pre>{@code
 * ActOneBestiary.init();
 * MapNodeType type = node.type();
 * EncounterCategory category = switch (type) {
 *     case BATTLE -> EncounterCategory.NORMAL;
 *     case ELITE  -> EncounterCategory.ELITE;
 *     case BOSS   -> EncounterCategory.BOSS;
 *     default     -> throw new IllegalStateException("非战斗节点");
 * };
 * EncounterDefinition encounter = EncounterCatalog
 *         .random(category, runState.currentAct(), new Random(runSeed))
 *         .orElseThrow();
 * List<Monster> monsters = encounter.createMonsters();
 * }</pre>
 *
 * <p>同一层可以注册多组普通遭遇，靠 {@link #random} 随机挑一组，
 * 不需要在战斗模块里写 if/else。</p>
 */
public final class EncounterCatalog {

    private static final Map<String, EncounterDefinition> REGISTRY = new LinkedHashMap<>();

    private EncounterCatalog() {
    }

    /**
     * 注册一场遭遇。
     *
     * @throws IllegalArgumentException ID 重复
     */
    public static void register(EncounterDefinition definition) {
        if (REGISTRY.containsKey(definition.id())) {
            throw new IllegalArgumentException("遭遇 ID 重复注册：" + definition.id());
        }
        REGISTRY.put(definition.id(), definition);
    }

    /** 按 ID 取遭遇。 */
    public static Optional<EncounterDefinition> find(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /**
     * 按 ID 取遭遇。
     *
     * @throws IllegalArgumentException 未注册
     */
    public static EncounterDefinition require(String id) {
        EncounterDefinition definition = REGISTRY.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的遭遇 ID：" + id
                    + "（请确认已调用对应层图鉴的 init()）");
        }
        return definition;
    }

    /** 某一层、某一档位的全部遭遇。 */
    public static List<EncounterDefinition> byCategory(EncounterCategory category, int act) {
        List<EncounterDefinition> result = new ArrayList<>();
        for (EncounterDefinition definition : REGISTRY.values()) {
            if (definition.category() == category && definition.act() == act) {
                result.add(definition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 随机抽一组遭遇——战斗模块生成敌人的标准做法。
     *
     * @param random 战斗随机源，请与 {@code RunState.runSeed} 保持一致以便复现
     * @return 抽中的遭遇；该层该档位没有配置时返回空
     */
    public static Optional<EncounterDefinition> random(EncounterCategory category, int act, Random random) {
        List<EncounterDefinition> pool = byCategory(category, act);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /** 全部已注册遭遇（只读）。 */
    public static List<EncounterDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /** 已注册数量。 */
    public static int size() {
        return REGISTRY.size();
    }

    /** 清空登记处，仅供测试与 Demo 重跑。 */
    public static void clear() {
        REGISTRY.clear();
    }
}
