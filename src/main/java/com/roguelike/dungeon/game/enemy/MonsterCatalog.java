package com.roguelike.dungeon.game.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 怪物登记处：怪物 ID → 定义的全局映射。
 *
 * <p>风格与项目里其它 catalog 保持一致（{@code CharacterCatalog} / {@code CardLibrary}），
 * 对外只暴露查询，注册走 {@link #register}。</p>
 *
 * <p><b>初始化约定：</b>各层的图鉴类（例如 {@link com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary}）
 * 负责把数据注册进来。战斗模块启动时先调用一次对应的 {@code init()}，之后就能按 ID 取怪了。</p>
 */
public final class MonsterCatalog {

    private static final Map<String, MonsterDefinition> REGISTRY = new LinkedHashMap<>();

    private MonsterCatalog() {
    }

    /**
     * 注册一只怪物。
     *
     * @throws IllegalArgumentException ID 重复
     */
    public static void register(MonsterDefinition definition) {
        if (REGISTRY.containsKey(definition.id())) {
            throw new IllegalArgumentException("怪物 ID 重复注册：" + definition.id());
        }
        REGISTRY.put(definition.id(), definition);
    }

    /** 批量注册。 */
    public static void registerAll(List<MonsterDefinition> definitions) {
        definitions.forEach(MonsterCatalog::register);
    }

    /**
     * 按 ID 取怪物定义。
     *
     * @throws IllegalArgumentException 未注册
     */
    public static MonsterDefinition require(String id) {
        MonsterDefinition definition = REGISTRY.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的怪物 ID：" + id
                    + "（请确认已调用对应层图鉴的 init()，例如 ActOneBestiary.init()）");
        }
        return definition;
    }

    /** 按 ID 取怪物定义，找不到时返回空。 */
    public static Optional<MonsterDefinition> find(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /** 全部已注册的怪物（只读，按注册顺序）。 */
    public static List<MonsterDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /** 按标签筛选怪物，例如 {@code byTag("act1")}、{@code byTag("normal")}。 */
    public static List<MonsterDefinition> byTag(String tag) {
        List<MonsterDefinition> result = new ArrayList<>();
        for (MonsterDefinition definition : REGISTRY.values()) {
            if (definition.hasTag(tag)) {
                result.add(definition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 已注册数量。 */
    public static int size() {
        return REGISTRY.size();
    }

    /**
     * 清空登记处。
     *
     * <p>仅供测试与 Demo 重跑使用，正常运行期不要调用。</p>
     */
    public static void clear() {
        REGISTRY.clear();
    }
}
