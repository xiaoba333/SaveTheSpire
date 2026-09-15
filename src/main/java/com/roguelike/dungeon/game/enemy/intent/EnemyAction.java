package com.roguelike.dungeon.game.enemy.intent;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;

/**
 * 怪物行动：真正「做事」的那一层。
 *
 * <p>{@link Intent} 负责「说」，{@code EnemyAction} 负责「做」。拆开的好处是：
 * UI 只需要读意图文本与图标，不需要理解行动逻辑；行动逻辑则可以脱离意图被复用
 * （例如把某个 action 塞进 {@link Intents#composite} 组合出更复杂的意图）。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>不要把行动写进 {@code Monster}，一个行动的代码不要超过十几行。</li>
 *   <li>{@code self} 可能在本行动中死亡（例如「破裂」），执行前后都要判 {@link Monster#isDead()}。</li>
 *   <li>所有对外效果一律通过 {@link BattleContext} 出口，禁止直接 new 战斗对象。</li>
 * </ul>
 */
@FunctionalInterface
public interface EnemyAction {

    /**
     * 执行本次行动。
     *
     * @param self 行动的发出者
     * @param ctx  战斗上下文
     */
    void perform(Monster self, BattleContext ctx);
}
