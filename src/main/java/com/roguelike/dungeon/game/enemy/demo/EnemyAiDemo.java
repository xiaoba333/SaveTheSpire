package com.roguelike.dungeon.game.enemy.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.DamageContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.MonsterDefinition;
import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCatalog;
import com.roguelike.dungeon.game.enemy.encounter.EncounterDefinition;
import com.roguelike.dungeon.game.enemy.status.StatusRegistry;

/**
 * 怪物 AI 命令行演示：不需要 JavaFX，也不需要战斗界面，直接把第一层全部遭遇
 * 跑若干回合并把战斗日志打印出来。
 *
 * <p>它的作用是<b>让框架先跑起来</b>：策划可以拿它验证数值与意图循环是否符合设计案，
 * 战斗负责人可以拿它当接入 {@code BattleContext} 的参照实现。</p>
 *
 * <p>运行方式（IDEA 里直接运行 main，或用 Maven）：</p>
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=com.roguelike.dungeon.game.enemy.demo.EnemyAiDemo
 * </pre>
 * <p>没有 exec 插件时，用 {@code javac} + {@code java} 直接跑也可以，本类不依赖 JavaFX。</p>
 */
public final class EnemyAiDemo {

    private static final int PLAYER_MAX_HEALTH = 300;
    private static final int TURNS_PER_ENCOUNTER = 8;
    private static final long DEMO_SEED = 20260911L;

    /**
     * 演示用的「玩家输出」：每回合对第一只可攻击的怪物造成这么多伤害。
     *
     * <p>它的唯一目的是让演示能跑出<b>血量触发的分支</b>——不给怪物掉血的话，
     * 巨人遗骸永远停在 75 血，第二阶段（复苏）根本不会出现。</p>
     *
     * <p>蛋（tag 为 egg）会被跳过：设计上蛋是「玩家不去打它，它就自己孵化」的计时器，
     * 如果测试伤害顺手把蛋打死，Boss 的整条孵化链就看不到了。</p>
     */
    private static final int TEST_PLAYER_DAMAGE = 12;

    private EnemyAiDemo() {
    }

    public static void main(String[] args) {
        ActOneBestiary.init();
        printBestiarySummary();

        for (EncounterDefinition encounter : EncounterCatalog.all()) {
            runEncounter(encounter, TURNS_PER_ENCOUNTER);
        }
    }

    // ==================================================================
    // 演示流程
    // ==================================================================

    private static void printBestiarySummary() {
        System.out.println("================================================");
        System.out.println("Save The Spire —— 第一层「地牢外围」怪物 AI 演示");
        System.out.println("已注册怪物 " + com.roguelike.dungeon.game.enemy.MonsterCatalog.size()
                + " 只，遭遇 " + EncounterCatalog.size() + " 组");
        System.out.println("================================================\n");
    }

    private static void runEncounter(EncounterDefinition encounter, int turns) {
        // 每一场战斗都从同一份种子开始，方便对比不同怪物的行为
        Player player = new Player(PLAYER_MAX_HEALTH, 3);
        List<Monster> monsters = encounter.createMonsters();
        SimpleBattleContext ctx = new SimpleBattleContext(player, monsters, new Random(DEMO_SEED));

        System.out.println("################ " + encounter.id() + " ################");
        System.out.println("[" + encounter.category().chineseName() + "] " + encounter.note());
        System.out.println("出场：" + describeLineup(monsters));
        System.out.println("玩家：" + player.getHealth() + "/" + player.getMaxHealth() + "\n");

        for (int turn = 1; turn <= turns; turn++) {
            System.out.println("---------- 第 " + turn + " 回合 ----------");
            // 快照：本回合出手的只能是开局就在场上的怪，新孵出来的下回合再动
            for (Monster monster : new ArrayList<>(monsters)) {
                if (monster.isDead()) {
                    continue;
                }
                monster.onTurnStart(ctx);
                monster.planIntent(ctx);
                System.out.println("  [意图] " + monster.displayName() + "：" + monster.intentText());
                monster.takeTurn(ctx);
                monster.onTurnEnd(ctx);
                System.out.println("  [状态] " + describe(monster));
            }
            System.out.println("  [玩家] 生命 " + player.getHealth() + "/" + player.getMaxHealth()
                    + " 护甲 " + player.getArmor()
                    + (ctx.playerStatusText().isEmpty() ? "" : " 状态 " + ctx.playerStatusText()));

            // 模拟玩家输出，让血量触发的阶段变化能被观察到
            Monster testTarget = firstDamageableTarget(monsters);
            if (testTarget != null) {
                int loss = testTarget.receiveDamage(
                        new DamageContext(TEST_PLAYER_DAMAGE, testTarget, true, "demo-player"));
                System.out.println("  [测试] 玩家攻击 " + testTarget.displayName() + " "
                        + TEST_PLAYER_DAMAGE + " 点，实际掉血 " + loss
                        + " → " + testTarget.getHealth() + "/" + testTarget.getMaxHealth());
            }
            System.out.println();
        }
        System.out.println("本场结束，玩家剩余生命 " + player.getHealth());
        if (!ctx.cardsPushedToDrawPile().isEmpty()) {
            System.out.println("被塞进玩家抽牌堆的卡：" + ctx.cardsPushedToDrawPile());
        }
        System.out.println();
        System.out.println();
    }

    /** 挑一只可以被测试伤害攻击的怪物：存活的、且不是蛋（蛋要留着演孵化链）。 */
    private static Monster firstDamageableTarget(List<Monster> monsters) {
        for (Monster monster : monsters) {
            if (!monster.isDead() && !monster.hasTag("egg")) {
                return monster;
            }
        }
        return null;
    }

    private static String describeLineup(List<Monster> monsters) {        List<String> parts = new ArrayList<>();
        for (Monster monster : monsters) {
            parts.add(monster.displayName() + "(" + monster.getHealth() + "血)");
        }
        return String.join(" + ", parts);
    }

    private static String describe(Monster monster) {
        if (monster.isDead()) {
            return monster.displayName() + " 已倒下";
        }
        return monster.displayName() + " " + monster.getHealth() + "/" + monster.getMaxHealth()
                + " 护甲 " + monster.getArmor()
                + " 力量 " + monster.getStrength()
                + " 状态 " + monster.statusSummary();
    }

    // ==================================================================
    // 参照实现：战斗模块需要实现的 BattleContext
    // ==================================================================

    /**
     * {@link BattleContext} 的最小实现：只把效果落实在 {@link Player} 与怪物列表上，
     * 不做任何界面与卡牌逻辑。战斗负责人可以照这个形状接入真正的 {@code Combat}。
     */
    static final class SimpleBattleContext implements BattleContext {

        private final Player player;
        private final List<Monster> monsters;
        private final Random random;
        private final Map<String, Integer> playerStatuses = new LinkedHashMap<>();
        private final List<String> cardsPushedToDrawPile = new ArrayList<>();

        SimpleBattleContext(Player player, List<Monster> monsters, Random random) {
            this.player = player;
            this.monsters = monsters;
            this.random = random;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public void damagePlayer(int amount, Object source) {
            player.receiveDamage(amount);
        }

        @Override
        public void healPlayer(int amount) {
            player.heal(amount);
        }

        @Override
        public void applyStatusToPlayer(String statusId, int stacks, Object source) {
            playerStatuses.merge(statusId, stacks, Integer::sum);
        }

        @Override
        public void addCardToPlayerDrawPile(String cardId, int count) {
            for (int i = 0; i < count; i++) {
                cardsPushedToDrawPile.add(cardId);
            }
        }

        @Override
        public List<Monster> allMonsters() {
            return List.copyOf(monsters);
        }

        @Override
        public List<Monster> allies(Monster self) {
            List<Monster> result = new ArrayList<>();
            for (Monster monster : monsters) {
                if (monster != self && !monster.isDead()) {
                    result.add(monster);
                }
            }
            return result;
        }

        @Override
        public Optional<Monster> findAlly(Monster self, String monsterId) {
            return allies(self).stream()
                    .filter(monster -> monster.id().equals(monsterId))
                    .findFirst();
        }

        @Override
        public Monster transform(Monster oldForm, MonsterDefinition newForm) {
            int slot = monsters.indexOf(oldForm);
            Monster born = new Monster(newForm);
            if (slot >= 0) {
                monsters.set(slot, born);
            } else {
                monsters.add(born);
            }
            return born;
        }

        @Override
        public void log(String message) {
            System.out.println(message);
        }

        @Override
        public Random random() {
            return random;
        }

        String playerStatusText() {
            if (playerStatuses.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            playerStatuses.forEach((id, stacks) ->
                    parts.add(StatusRegistry.displayName(id) + " " + stacks));
            return String.join(" ｜ ", parts);
        }

        List<String> cardsPushedToDrawPile() {
            return cardsPushedToDrawPile;
        }
    }
}
