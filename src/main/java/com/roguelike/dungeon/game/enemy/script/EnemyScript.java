package com.roguelike.dungeon.game.enemy.script;

import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.intent.Intent;

/**
 * 怪物决策脚本：回答「这个怪物下一回合想干什么」。
 *
 * <p>它是怪物 AI 的大脑，也是唯一需要被替换掉就能换一种行为方式的地方。
 * 框架自带的实现见 {@link LoopScript}（固定循环）与 {@link PhaseScript}（按血量分阶段）。</p>
 *
 * <p><b>状态性约定：</b>脚本是「有状态」的——内部记录自己走到第几个回合。
 * 因此每一个怪物实例都必须持有<b>独立的脚本实例</b>，这就是
 * {@code MonsterDefinition} 用工厂（{@code IntFunction<EnemyScript>}）
 * 而不是直接存一个脚本对象的原因。</p>
 *
 * <p><b>调用约定：</b>{@link Monster#planIntent} 每回合调用一次，
 * 每次调用把内部回合计数+1 并返回该回合的意图。</p>
 */
public interface EnemyScript {

    /**
     * 生成下一回合的意图。
     *
     * @param self 脚本所属的怪物
     * @param ctx  战斗上下文（需要看同伴、看玩家状态时用）
     * @return 本回合意图，不可为 {@code null}
     */
    Intent nextIntent(Monster self, BattleContext ctx);
}
