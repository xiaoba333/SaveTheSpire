package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.entity.RelicTrigger;

import java.util.Objects;
import java.util.Set;

/**
 * 用 lambda 描述的遗物，适用于<b>无状态</b>遗物。
 *
 * <p>风格与 {@code CardLibrary} 里用 lambda 定义卡牌保持一致：
 * 效果写在 {@code RelicLibrary} 中一眼可见，不必为每个遗物开一个类。</p>
 *
 * <p>带有累计计数（苦无的攻击数）或跨回合暂存（钙化壳的护甲）的遗物，
 * 不能使用本类——单例会导致状态在多次开局之间串味，请写成独立类。</p>
 */
public final class SimpleRelic implements Relic {

    /** 遗物触发时的处理逻辑。 */
    @FunctionalInterface
    public interface Handler {
        void onTrigger(RelicTrigger trigger, RelicContext ctx);
    }

    private final String id;
    private final String name;
    private final String description;
    private final RelicRarity rarity;
    private final Set<RelicTrigger> triggers;
    private final Handler handler;

    public SimpleRelic(
            String id,
            String name,
            String description,
            RelicRarity rarity,
            Set<RelicTrigger> triggers,
            Handler handler) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("遗物编号不能为空");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "遗物名称不能为 null");
        this.description = Objects.requireNonNull(description, "遗物描述不能为 null");
        this.rarity = Objects.requireNonNull(rarity, "遗物稀有度不能为 null");
        this.triggers = Set.copyOf(Objects.requireNonNull(triggers, "触发点集合不能为 null"));
        this.handler = Objects.requireNonNull(handler, "遗物处理逻辑不能为 null");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public RelicRarity rarity() {
        return rarity;
    }

    @Override
    public Set<RelicTrigger> triggers() {
        return triggers;
    }

    @Override
    public void onTrigger(RelicTrigger trigger, RelicContext ctx) {
        handler.onTrigger(trigger, ctx);
    }

    @Override
    public String toString() {
        return name + "（" + rarity.displayName() + "）";
    }
}
