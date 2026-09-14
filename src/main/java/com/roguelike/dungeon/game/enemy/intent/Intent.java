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
 * {@code iconKey} 是给 UI 找图标的键，{@code amount} 是叠在图标上的那个数字，
 * {@code action} 是本回合实际执行的逻辑。</p>
 *
 * @param type        意图类型（决定图标大类）
 * @param displayText 展示文本，例如 "打6，给予 1 层易伤"
 * @param iconKey     图标资源键，例如 "intent_attack"
 * @param amount      图标上要显示的数字：攻击是伤害、防御是格挡量、削弱是层数。
 *                    <b>0 表示没有数字可显示</b>（UI 据此不画），不是「显示 0」。
 *                    攻击显示的是<b>不含力量的基础值</b>——加力量由 AI 生成快照时补，
 *                    因为同一份意图在力量变化后要显示不同的数。
 * @param action      执行体，不可为 null
 */
public record Intent(IntentType type, String displayText, String iconKey, int amount, EnemyAction action) {

    public Intent {
        if (action == null) {
            throw new IllegalArgumentException("意图必须绑定一个行动：" + displayText);
        }
    }

    /**
     * 兼容旧写法：不带可显示的数字（{@code amount} 记 0）。
     *
     * <p>「什么都不做」「塞牌」这类本来就没有数字的意图用它就行，不用跟着改。</p>
     */
    public Intent(IntentType type, String displayText, String iconKey, EnemyAction action) {
        this(type, displayText, iconKey, 0, action);
    }

    /** 换一个展示文本，其余不变（用于按怪物名做个性化描述）。 */
    public Intent withText(String newText) {
        return new Intent(type, newText, iconKey, amount, action);
    }

    /** 换一个图标键，其余不变。 */
    public Intent withIcon(String newIconKey) {
        return new Intent(type, displayText, newIconKey, amount, action);
    }

    @Override
    public String toString() {
        return "[" + type.chineseName() + "] " + displayText;
    }
}
