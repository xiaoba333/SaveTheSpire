package com.roguelike.dungeon.game.enemy;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.roguelike.dungeon.game.entity.Player;

/**
 * 战斗上下文：怪物 AI 与「战斗模块」之间唯一的通信口岸。
 *
 * <p>AI 只依赖这个接口，<b>不依赖任何具体的战斗页面、抽牌堆或回合控制器</b>。
 * 由战斗负责人在 {@code CombatController} 里实现它，把伤害、状态、抽牌等
 * 实际操作转交给现有战斗系统。</p>
 *
 * <p>实现方的硬性约定：</p>
 * <ul>
 *   <li>{@link #damagePlayer} 必须走「护甲 → 血量」的完整结算（见《实体接口规范》5.1）。</li>
 *   <li>{@link #allMonsters()} 返回的列表必须是只读视图，AI 不能借此直接增删怪物。</li>
 *   <li>变换形态只允许通过 {@link #transform}，由战斗模块统一维护出场槽位。</li>
 * </ul>
 */
public interface BattleContext {

    /** 本场战斗的玩家实例。 */
    Player player();

    /**
     * 对玩家造成伤害（护甲先吸收，剩余由血量承担）。
     *
     * @param amount 伤害数值
     * @param source 伤害来源（通常传怪物自身，供日志与斩杀统计使用）
     */
    void damagePlayer(int amount, Object source);

    /** 治疗玩家，不超过玩家最大生命。 */
    void healPlayer(int amount);

    /**
     * 给玩家挂状态（易伤 / 虚弱 / 中毒等）。
     *
     * <p>AI 只负责「施加」，具体数值结算由玩家状态模块实现。</p>
     */
    void applyStatusToPlayer(String statusId, int stacks, Object source);

    /** 向玩家抽牌堆随机位置插入卡牌，例如 Boss 凯洛斯的「甲片」。 */
    void addCardToPlayerDrawPile(String cardId, int count);

    /** 场上所有怪物（含已死亡的，只读）。 */
    List<Monster> allMonsters();

    /** 除自己以外的存活怪物（只读）。 */
    List<Monster> allies(Monster self);

    /** 按怪物定义 ID 找一名存活同伴，例如探险者男要找「探险者女」。 */
    Optional<Monster> findAlly(Monster self, String monsterId);

    /**
     * 形态变换：把 {@code oldForm} 从场上移除，并在同一个出场槽位生成
     * {@code newForm} 的新实例（Boss 凯洛斯「蛋 → 破壳而出」靠它实现）。
     *
     * @return 新生成的怪物实例
     */
    Monster transform(Monster oldForm, MonsterDefinition newForm);

    /** 追加一条战斗日志（UI 展示 / 调试用）。 */
    void log(String message);

    /** 本场战斗的随机源（由 {@code RunState.runSeed} 派生，保证同种子可复现）。 */
    Random random();
}
