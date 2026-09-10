package com.roguelike.dungeon.game.battle;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 怪物目录：按种子从「第一章前几场」的普通怪物里挑一只。
 */
public final class MonsterCatalog {

    private static final List<Supplier<MonsterAi>> EASY_POOL = List.of(
            CultistAi::new,
            JawWormAi::new,
            LouseAi::new,
            AcidSlimeAi::new);

    private MonsterCatalog() {
    }

    /**
     * 按种子确定性挑选一只普通怪物。
     *
     * @param seed 本局 / 本节点的种子
     * @return 一个全新的怪物 AI 实例
     */
    public static MonsterAi randomEasy(long seed) {
        Random random = new Random(seed);
        return EASY_POOL.get(random.nextInt(EASY_POOL.size())).get();
    }
}
