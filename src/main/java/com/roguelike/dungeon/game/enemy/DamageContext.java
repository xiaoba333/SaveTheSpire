package com.roguelike.dungeon.game.enemy;

/**
 * 伤害结算上下文：在这条「一次伤害」的管道里传递伤害数值与来源信息。
 *
 * <p>它存在的意义是让状态效果能够「修改即将承受的伤害」——例如
 * {@code 蠕动} 把首次攻击伤害减半、{@code 骨质疏松} 让受到的伤害 +2。
 * 状态只改 {@link #amount()}，不做实际结算；实际结算由
 * {@link Monster#receiveDamage(DamageContext)} 完成。</p>
 *
 * <p>生命周期：一次伤害 = 一个 {@code DamageContext} 实例，用完即弃，不要缓存复用。</p>
 */
public final class DamageContext {

    private int amount;
    private final Monster target;
    private final boolean attack;
    private final Object source;
    private int actualHealthLoss;

    /**
     * @param amount 伤害数值（修正前）
     * @param target 承受伤害的怪物
     * @param attack 是否属于「攻击」伤害（{@code 蠕动} 只对攻击生效）
     * @param source 伤害来源，可为 {@code null}（玩家、卡牌、状态……）
     */
    public DamageContext(int amount, Monster target, boolean attack, Object source) {
        this.amount = Math.max(0, amount);
        this.target = target;
        this.attack = attack;
        this.source = source;
    }

    /** 当前伤害数值（可能已被状态修正）。 */
    public int amount() {
        return amount;
    }

    /** 直接改写伤害数值，负数会被夹到 0。 */
    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    /** 增减伤害数值。 */
    public void addAmount(int delta) {
        setAmount(amount + delta);
    }

    /** 伤害减半（向下取整），{@code 蠕动} 用它实现「首次受到攻击时伤害减半」。 */
    public void halve() {
        setAmount(amount / 2);
    }

    /** 按倍率缩放伤害，结果四舍五入。 */
    public void multiply(double factor) {
        setAmount((int) Math.round(amount * factor));
    }

    /** 是否属于「攻击」伤害。 */
    public boolean isAttack() {
        return attack;
    }

    /** 承受伤害的目标。 */
    public Monster target() {
        return target;
    }

    /** 伤害来源，可能为 {@code null}。 */
    public Object source() {
        return source;
    }

    /** 结算后实际扣掉的血量（护甲吸收之后真正落在血量上的部分）。 */
    public int actualHealthLoss() {
        return actualHealthLoss;
    }

    /** 由 {@link Monster} 在结算完成后回填，外部不要调用。 */
    void setActualHealthLoss(int actualHealthLoss) {
        this.actualHealthLoss = actualHealthLoss;
    }

    @Override
    public String toString() {
        return "DamageContext{amount=" + amount + ", attack=" + attack + ", target=" + target.displayName() + "}";
    }
}
