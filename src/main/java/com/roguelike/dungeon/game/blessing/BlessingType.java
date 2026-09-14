package com.roguelike.dungeon.game.blessing;

/** 开局房间可选的馈赠类型。 */
public enum BlessingType {
    REMOVE_CARD(
            "remove_card",
            "删除一张牌",
            "从永久牌组中移除一张牌。",
            true),
    UPGRADE_CARD(
            "upgrade_card",
            "升级一张牌",
            "选择永久牌组中的一张牌升级。",
            true),
    MAX_HP(
            "max_hp",
            "最大生命值 +6",
            "最大生命值增加 6 点，并回复等量生命。",
            false),
    GOLD(
            "gold",
            "获得 100 金币",
            "立刻获得 100 金币。",
            false),
    DAMAGE_GOLD(
            "damage_gold",
            "受到 10 点伤害，获得 200 金币",
            "失去 10 点生命，立刻获得 200 金币。",
            false);

    private final String id;
    private final String label;
    private final String description;
    private final boolean requiresCard;

    BlessingType(String id, String label, String description, boolean requiresCard) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.requiresCard = requiresCard;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean requiresCard() {
        return requiresCard;
    }

    public static BlessingType byId(String id) {
        for (BlessingType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
