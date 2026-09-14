package com.roguelike.dungeon.game.blessing;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.run.RunState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 选角后的开局房间：从五种馈赠里随机抽出三个互不相同的选项。
 */
public final class BlessingService {
    public static final int CHOICE_COUNT = 3;
    public static final int MAX_HEALTH_BONUS = 6;
    public static final int GOLD_BONUS = 100;
    public static final int DAMAGE_COST = 10;
    public static final int DAMAGE_GOLD_BONUS = 200;

    public static final String TITLE = "开局房间";
    public static final String DESCRIPTION = "高塔底层有一间安静的房间。三份馈赠正等着你，选一个带走。";

    private final RunState runState;
    private final List<BlessingType> offered;

    private BlessingType pendingType;
    private boolean resolved;

    public BlessingService(RunState runState, long seed) {
        this(runState, rollTypes(seed));
    }

    /** 供测试注入固定选项。 */
    BlessingService(RunState runState, List<BlessingType> offered) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        List<BlessingType> copy = List.copyOf(Objects.requireNonNull(
                offered, "祝福选项不能为 null"));
        if (copy.size() != CHOICE_COUNT) {
            throw new IllegalArgumentException("开局房间必须提供三个选项");
        }
        copy.forEach(type -> Objects.requireNonNull(type, "祝福选项不能包含 null"));
        this.offered = copy;
    }

    /** 按种子从五种馈赠中抽出三个，同一种子结果固定。 */
    public static List<BlessingType> rollTypes(long seed) {
        List<BlessingType> types = new ArrayList<>(List.of(BlessingType.values()));
        Collections.shuffle(types, new Random(seed));
        return List.copyOf(types.subList(0, CHOICE_COUNT));
    }

    public List<BlessingOption> getOptions() {
        List<BlessingOption> options = new ArrayList<>(offered.size());
        for (BlessingType type : offered) {
            options.add(toOption(type));
        }
        return List.copyOf(options);
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean isAwaitingCard() {
        return pendingType != null && !resolved;
    }

    public BlessingType pendingType() {
        return pendingType;
    }

    /** 当前待选卡牌：删卡给整副牌组，升级只给可升级的牌。 */
    public List<CardInstance> getTargetCards() {
        if (pendingType == BlessingType.REMOVE_CARD) {
            return runState.getDeck();
        }
        if (pendingType == BlessingType.UPGRADE_CARD) {
            return upgradableCards();
        }
        return List.of();
    }

    /** 选择一个馈赠。需要指定卡牌的选项会进入待选状态，不会立刻结束房间。 */
    public BlessingActionResult choose(String optionId) {
        if (resolved) {
            return new BlessingActionResult(
                    BlessingActionStatus.ALREADY_RESOLVED,
                    "开局馈赠已经领取。");
        }
        BlessingType type = offered.stream()
                .filter(candidate -> candidate.id().equals(optionId))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return new BlessingActionResult(
                    BlessingActionStatus.CHOICE_NOT_FOUND,
                    "祝福选项不存在：" + optionId);
        }
        BlessingOption view = toOption(type);
        if (!view.available()) {
            return new BlessingActionResult(
                    BlessingActionStatus.CHOICE_UNAVAILABLE,
                    view.unavailableReason());
        }
        if (type.requiresCard()) {
            pendingType = type;
            return new BlessingActionResult(
                    BlessingActionStatus.NEEDS_CARD,
                    "请选择一张牌。");
        }
        return applyInstant(type);
    }

    /** 为删卡或升级指定一张永久牌组中的牌。 */
    public BlessingActionResult chooseCard(String cardInstanceId) {
        if (resolved) {
            return new BlessingActionResult(
                    BlessingActionStatus.ALREADY_RESOLVED,
                    "开局馈赠已经领取。");
        }
        if (pendingType == null) {
            return new BlessingActionResult(
                    BlessingActionStatus.NOT_WAITING_FOR_CARD,
                    "当前不需要选择卡牌。");
        }
        if (pendingType == BlessingType.REMOVE_CARD) {
            return removeCard(cardInstanceId);
        }
        return upgradeCard(cardInstanceId);
    }

    /** 取消待选卡牌，回到三个选项。 */
    public void cancelPending() {
        pendingType = null;
    }

    private BlessingActionResult applyInstant(BlessingType type) {
        return switch (type) {
            case MAX_HP -> {
                runState.getPlayer().increaseMaxHealth(MAX_HEALTH_BONUS);
                yield complete("最大生命值增加 " + MAX_HEALTH_BONUS + " 点。");
            }
            case GOLD -> {
                runState.addGold(GOLD_BONUS);
                yield complete("获得了 " + GOLD_BONUS + " 金币。");
            }
            case DAMAGE_GOLD -> {
                int lost = runState.getPlayer().takeDamage(DAMAGE_COST);
                runState.addGold(DAMAGE_GOLD_BONUS);
                yield complete("受到 " + lost + " 点伤害，获得了 "
                        + DAMAGE_GOLD_BONUS + " 金币。");
            }
            default -> new BlessingActionResult(
                    BlessingActionStatus.CHOICE_UNAVAILABLE,
                    "该选项需要先选择一张牌。");
        };
    }

    private BlessingActionResult removeCard(String cardInstanceId) {
        CardInstance card = findDeckCard(cardInstanceId);
        if (card == null) {
            return new BlessingActionResult(
                    BlessingActionStatus.CARD_NOT_FOUND,
                    "永久牌组中不存在该卡牌：" + cardInstanceId);
        }
        runState.removeCard(cardInstanceId);
        return complete("已从牌组中删除「" + card.displayName() + "」。");
    }

    private BlessingActionResult upgradeCard(String cardInstanceId) {
        CardInstance card = findDeckCard(cardInstanceId);
        if (card == null) {
            return new BlessingActionResult(
                    BlessingActionStatus.CARD_NOT_FOUND,
                    "永久牌组中不存在该卡牌：" + cardInstanceId);
        }
        if (card.upgraded() || !card.card().upgradable()) {
            return new BlessingActionResult(
                    BlessingActionStatus.CARD_NOT_UPGRADABLE,
                    "这张卡牌不能继续升级。");
        }
        if (!runState.upgradeCard(card.upgradedCopy())) {
            return new BlessingActionResult(
                    BlessingActionStatus.CARD_NOT_UPGRADABLE,
                    "这张卡牌不能继续升级。");
        }
        return complete("「" + card.displayName() + "」已升级。");
    }

    private BlessingActionResult complete(String message) {
        resolved = true;
        pendingType = null;
        return new BlessingActionResult(BlessingActionStatus.SUCCESS, message);
    }

    private BlessingOption toOption(BlessingType type) {
        boolean available = true;
        String reason = "";
        if (type == BlessingType.REMOVE_CARD && runState.getDeck().isEmpty()) {
            available = false;
            reason = "永久牌组是空的。";
        } else if (type == BlessingType.UPGRADE_CARD && upgradableCards().isEmpty()) {
            available = false;
            reason = "当前牌组中没有可以升级的卡牌。";
        }
        return new BlessingOption(
                type.id(),
                type.label(),
                type.description(),
                type.requiresCard(),
                available,
                reason);
    }

    private List<CardInstance> upgradableCards() {
        return runState.getDeck().stream()
                .filter(card -> !card.upgraded() && card.card().upgradable())
                .toList();
    }

    private CardInstance findDeckCard(String cardInstanceId) {
        if (cardInstanceId == null || cardInstanceId.isBlank()) {
            return null;
        }
        return runState.getDeck().stream()
                .filter(card -> card.id().equals(cardInstanceId))
                .findFirst()
                .orElse(null);
    }
}
