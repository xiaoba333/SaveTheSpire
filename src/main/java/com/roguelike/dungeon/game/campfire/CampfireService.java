package com.roguelike.dungeon.game.campfire;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.Objects;

/** 管理一个篝火节点的休息、锻造和离开操作。 */
public final class CampfireService {
    public static final int REST_HEAL_PERCENT = 30;

    private static final String REST_ID = "rest";
    private static final String SMITH_ID = "smith";
    private static final String LEAVE_ID = "leave";

    private final RunState runState;
    private final LevelFinishHandler finishHandler;

    private boolean used;

    public CampfireService(RunState runState, LevelFinishHandler finishHandler) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.finishHandler = Objects.requireNonNull(
                finishHandler, "关卡结束处理器不能为 null");
    }

    /** 返回根据当前生命和牌组计算出的操作快照。 */
    public List<CampfireAction> getActions() {
        Player player = runState.getPlayer();
        boolean canRest = player.getHealth() < player.getMaxHealth();
        boolean canSmith = !getUpgradeableCards().isEmpty();
        int healAmount = calculateRestHeal(player.getMaxHealth());
        return List.of(
                new CampfireAction(
                        REST_ID,
                        "休息",
                        "恢复最多 " + healAmount + " 点生命（最大生命的 "
                                + REST_HEAL_PERCENT + "%）。",
                        canRest,
                        canRest ? "" : "当前生命值已满。"),
                new CampfireAction(
                        SMITH_ID,
                        "锻造",
                        "选择永久牌组中的一张牌进行升级。",
                        canSmith,
                        canSmith ? "" : "当前牌组中没有可以升级的卡牌。"),
                new CampfireAction(
                        LEAVE_ID,
                        "离开",
                        "不进行操作，离开篝火。",
                        true,
                        ""));
    }

    /** 返回未升级且允许升级的永久牌组卡牌。 */
    public List<CardInstance> getUpgradeableCards() {
        return runState.getDeck().stream()
                .filter(card -> !card.upgraded() && card.card().upgradable())
                .toList();
    }

    public boolean isUsed() {
        return used;
    }

    /** 恢复最大生命值的 30%，至少恢复 1 点且不超过生命上限。 */
    public CampfireActionResult rest() {
        CampfireActionResult alreadyUsed = rejectIfUsed();
        if (alreadyUsed != null) {
            return alreadyUsed;
        }
        Player player = runState.getPlayer();
        if (player.getHealth() >= player.getMaxHealth()) {
            return new CampfireActionResult(
                    CampfireActionStatus.ACTION_UNAVAILABLE,
                    "当前生命值已满，无法休息。");
        }

        int before = player.getHealth();
        player.heal(calculateRestHeal(player.getMaxHealth()));
        int healed = player.getHealth() - before;
        return complete("你在篝火旁休息，恢复了 " + healed + " 点生命。");
    }

    /** 升级指定的永久牌组卡牌。 */
    public CampfireActionResult smith(String cardInstanceId) {
        CampfireActionResult alreadyUsed = rejectIfUsed();
        if (alreadyUsed != null) {
            return alreadyUsed;
        }
        CardInstance card = runState.getDeck().stream()
                .filter(candidate -> candidate.id().equals(cardInstanceId))
                .findFirst()
                .orElse(null);
        if (card == null) {
            return new CampfireActionResult(
                    CampfireActionStatus.CARD_NOT_FOUND,
                    "永久牌组中不存在该卡牌：" + cardInstanceId);
        }
        if (card.upgraded() || !card.card().upgradable()) {
            return new CampfireActionResult(
                    CampfireActionStatus.CARD_NOT_UPGRADABLE,
                    "这张卡牌不能继续升级。");
        }
        if (!runState.upgradeCard(card.upgradedCopy())) {
            return new CampfireActionResult(
                    CampfireActionStatus.CARD_NOT_UPGRADABLE,
                    "这张卡牌不能继续升级。");
        }
        return complete("锻造完成：「" + card.displayName() + "」已升级。");
    }

    /** 不进行其他操作，直接离开篝火。 */
    public CampfireActionResult leave() {
        CampfireActionResult alreadyUsed = rejectIfUsed();
        if (alreadyUsed != null) {
            return alreadyUsed;
        }
        return complete("你离开了篝火。");
    }

    static int calculateRestHeal(int maxHealth) {
        return Math.max(1, maxHealth * REST_HEAL_PERCENT / 100);
    }

    private CampfireActionResult rejectIfUsed() {
        if (!used) {
            return null;
        }
        return new CampfireActionResult(
                CampfireActionStatus.CAMPFIRE_ALREADY_USED,
                "当前篝火已经使用过了。");
    }

    private CampfireActionResult complete(String message) {
        used = true;
        finishHandler.onLevelFinished(LevelResult.COMPLETED);
        return new CampfireActionResult(CampfireActionStatus.SUCCESS, message);
    }
}
