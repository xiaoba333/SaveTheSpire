package com.roguelike.dungeon.game.enemy.intent;

/**
 * 一个回合的意图：显示什么 + 做什么。
 *
 * <p>对应设计案里写的一行，例如</p>
 * <pre>
 * 打6，给予玩家一层易伤
 * 防6并获得2力量
 * 打2*3（2伤害3次）
 * 向玩家抽牌堆增加一张甲片
 * </pre>
 *
 * <p>{@code displayText} 是给玩家看的原文（可直接显示在怪物头顶），
 * {@code iconKey} 是给 UI 找图标的键，{@code action} 是本回合实际执行的逻辑。</p>
 *
 * @param type        意图类型（决定图标大类）
 * @param displayText 展示文本，例如 "打6，给予 1 层易伤"
 * @param iconKey     图标资源键，例如 "intent_attack"
 * @param action      执行体，不可为 null
 */
public record Intent(IntentType type, String displayText, String iconKey, EnemyAction action) {

    public Intent {
        if (action == null) {
            throw new IllegalArgumentException("意图必须绑定一个行动：" + displayText);
        }
    }

    /** 换一个展示文本，其余不变（用于按怪物名做个性化描述）。 */
    public Intent withText(String newText) {
        return new Intent(type, newText, iconKey, action);
    }

    /** 换一个图标键，其余不变。 */
    public Intent withIcon(String newIconKey) {
        return new Intent(type, displayText, newIconKey, action);
    }

    @Override
    public String toString() {
        return "[" + type.chineseName() + "] " + displayText;
    }
}
