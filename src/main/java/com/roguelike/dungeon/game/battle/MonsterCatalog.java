package com.roguelike.dungeon.game.battle;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 怪物目录：按节点类型与种子挑选怪物。
 *
 * <p>三个池子互相独立：普通战斗只从 {@link #EASY_POOL} 里挑轻松怪，精英走
 * {@link #ELITE_POOL}，Boss 走 {@link #BOSS_POOL}。后两者不再复用普通怪的攻防循环。</p>
 */
public final class MonsterCatalog {

    /** 普通战斗怪物池。 */
    private static final List<Supplier<MonsterAi>> EASY_POOL = List.of(
            CultistAi::new,
            JawWormAi::new,
            LouseAi::new,
            AcidSlimeAi::new,
            SporeFungusAi::new,
            GargoyleAi::new,
            ToxicSlimeAi::new);

    /** 精英怪物池。 */
    private static final List<Supplier<MonsterAi>> ELITE_POOL = List.of(
            BloodFrenzyAi::new,
            TwinHeadedHoundAi::new);

    /** Boss 池；目前只有熔岩领主，后续可继续追加。 */
    private static final List<Supplier<MonsterAi>> BOSS_POOL = List.of(
            MagmaLordAi::new);

    private MonsterCatalog() {
    }

    /**
     * 按种子确定性挑选一只普通怪物。
     *
     * @param seed 本局 / 本节点的种子
     * @return 一个全新的怪物 AI 实例
     */
    public static MonsterAi randomEasy(long seed) {
        return pick(EASY_POOL, seed);
    }

    /** 按种子确定性挑选一只精英怪。 */
    public static MonsterAi randomElite(long seed) {
        return pick(ELITE_POOL, seed);
    }

    /** 按种子确定性挑选一只 Boss。 */
    public static MonsterAi randomBoss(long seed) {
        return pick(BOSS_POOL, seed);
    }

    private static MonsterAi pick(List<Supplier<MonsterAi>> pool, long seed) {
        return pool.get(new Random(seed).nextInt(pool.size())).get();
    }
}
