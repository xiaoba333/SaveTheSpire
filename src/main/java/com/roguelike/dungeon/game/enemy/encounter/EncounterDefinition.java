package com.roguelike.dungeon.game.enemy.encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.MonsterCatalog;

/**
 * 一次遭遇的编队定义：这场战斗里出场的是哪几只怪、按什么顺序站。
 *
 * <p>地图模块只关心「这是个精英节点」，具体打谁由本模块决定——
 * 这正是《地图与关卡模块协作说明》里点名要战斗负责人补上的能力
 * 「根据 node.type() 区分普通、精英和 Boss」。</p>
 *
 * @param id       遭遇 ID，例如 {@code "act1_grubs"}
 * @param category 难度档位
 * @param act      所属层数（1 = 地牢外围）
 * @param spawns   出场怪物，顺序即站位顺序（左 → 右）
 * @param note     备注，写清楚这场的特殊机制
 */
public record EncounterDefinition(String id,
                                  EncounterCategory category,
                                  int act,
                                  List<MonsterSpawn> spawns,
                                  String note) {

    public EncounterDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (spawns == null || spawns.isEmpty()) {
            throw new IllegalArgumentException("遭遇必须至少有一只怪物：" + id);
        }
        spawns = List.copyOf(spawns);
    }

    /**
     * 出场条目：某只怪，以及它在同类中的序号。
     *
     * <p>{@code variant} 的意义是让同一种怪有细微差别。最典型的例子是两条蛆：
     * 两者数值完全相同，但一只先「打6」、另一只先「上虚弱」，形成设计案里
     * 说的「交错进行」。</p>
     *
     * @param monsterId 怪物定义 ID
     * @param variant   同类序号（0 起）
     */
    public record MonsterSpawn(String monsterId, int variant) {

        public MonsterSpawn {
            Objects.requireNonNull(monsterId, "monsterId");
        }

        public static MonsterSpawn of(String monsterId) {
            return new MonsterSpawn(monsterId, 0);
        }
    }

    /**
     * 按本编队生成怪物实例。每次调用都是全新实例，血量与状态互不共享。
     *
     * @throws IllegalArgumentException 引用了未注册的怪物 ID
     */
    public List<Monster> createMonsters() {
        List<Monster> monsters = new ArrayList<>(spawns.size());
        for (MonsterSpawn spawn : spawns) {
            monsters.add(new Monster(MonsterCatalog.require(spawn.monsterId()), spawn.variant()));
        }
        return monsters;
    }

    /** 出场怪物数量。 */
    public int size() {
        return spawns.size();
    }

    @Override
    public String toString() {
        return "[" + category.chineseName() + "] " + id + " → "
                + spawns.stream()
                .map(s -> s.monsterId() + "#" + s.variant())
                .reduce((a, b) -> a + ", " + b)
                .orElse("空");
    }
}
