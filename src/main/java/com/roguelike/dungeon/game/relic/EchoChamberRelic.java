package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;

import java.util.Set;

/**
 * 遗物「回响之室」：每回合打出的第 1 张牌费用为 0。
 *
 * <p>实现依赖两个钩子配合：
 * <ul>
 *   <li>{@link Relic#modifyCost} 在出牌前被询问（费用校验之前），返回 0 即免费；</li>
 *   <li>{@link RelicTrigger#TURN_START} 重置「本回合免费额度」，{@link RelicTrigger#CARD_PLAYED}
 *       标记额度已用掉。</li>
 * </ul>
 * 三者的顺序由 {@code Combat} / {@code CardPlayService} 保证：回合开始先刷新能量再分发
 * TURN_START，出牌时先算费用、成功打出后才分发 CARD_PLAYED。因此「费用不足被打回」
 * 不会白白消耗额度。</p>
 *
 * <p><b>不含 X 费用牌</b>：{@code 血雨}（X 费用）在 {@code CardPlayService} 里走的是
 * 「消耗当前全部能量」的独立分支，不经过遗物费用修正。</p>
 *
 * <p>本遗物带「本回合额度是否已用」的状态，必须每次创建新实例。</p>
 */
public final class EchoChamberRelic implements Relic {

    /** 本回合的免费额度是否已经用掉。 */
    private boolean freeCardUsed;

    @Override
    public String id() {
        return "echo_chamber";
    }

    @Override
    public String name() {
        return "回响之室";
    }

    @Override
    public String description() {
        return "每回合打出的第 1 张牌费用为 0（X 费用牌除外）。";
    }

    @Override
    public RelicRarity rarity() {
        return RelicRarity.RARE;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return Set.of(RelicTrigger.TURN_START, RelicTrigger.CARD_PLAYED);
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        if (trigger == RelicTrigger.TURN_START) {
            freeCardUsed = false;      // 新回合，额度恢复
        } else {
            freeCardUsed = true;       // 已有牌打出，本回合额度用掉
        }
    }

    @Override
    public int modifyCost(CardInstance instance, int currentCost) {
        return freeCardUsed ? currentCost : 0;
    }
}
