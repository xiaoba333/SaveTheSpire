package com.roguelike.dungeon.game.battle;

/**
 * 默认怪物 AI：攻击与叠甲交替循环（保留 MVP 原有行为）。
 *
 * <p>也作为可配置的通用怪物使用——按名字、血量、攻击、叠甲构造，
 * 可复现「先攻击、再叠甲」的简单节奏。</p>
 */
public final class DefaultMonsterAi implements MonsterAi {

    public static final int DEFAULT_MAX_HP = 30;
    public static final int DEFAULT_ATTACK = 10;
    public static final int DEFAULT_BLOCK = 10;

    private final String displayName;
    private final int maxHp;
    private final int attack;
    private final int block;
    private boolean willAttack = true;

    public DefaultMonsterAi() {
        this("守卫者", DEFAULT_MAX_HP, DEFAULT_ATTACK, DEFAULT_BLOCK);
    }

    public DefaultMonsterAi(String displayName, int maxHp, int attack, int block) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.attack = attack;
        this.block = block;
    }

    @Override
    public String name() {
        return displayName;
    }

    @Override
    public int maxHp() {
        return maxHp;
    }

    @Override
    public Combat.Intent nextIntent() {
        return willAttack
                ? new Combat.Intent("ATTACK", attack)
                : new Combat.Intent("DEFEND", block);
    }

    @Override
    public void startFight() {
        willAttack = true;
    }

    @Override
    public void takeTurn(Combat combat, int turnNumber) {
        if (willAttack) {
            int dealt = combat.applyDamage(false, attack);
            combat.log(name() + "攻击，对玩家造成 " + dealt + " 点伤害。");
        } else {
            combat.addMonsterBlockInternal(block);
            combat.log(name() + "防御，获得 " + block + " 点护盾。");
        }
        willAttack = !willAttack;
    }
}
