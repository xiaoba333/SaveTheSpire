package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.StatusEffect;
import com.roguelike.dungeon.game.enemy.BattleContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.MonsterDefinition;
import com.roguelike.dungeon.game.enemy.bestiary.ActOneBestiary;
import com.roguelike.dungeon.game.enemy.bestiary.ActOneExtraBestiary;
import com.roguelike.dungeon.game.enemy.encounter.EncounterCatalog;
import com.roguelike.dungeon.game.enemy.encounter.EncounterDefinition;
import com.roguelike.dungeon.game.enemy.intent.Intent;
import com.roguelike.dungeon.game.enemy.intent.IntentType;
import com.roguelike.dungeon.game.enemy.status.StatusIds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 多怪编队 AI：一个实例驱动一整组怪，接上现有 1v1 {@link Combat}。
 *
 * <p><b>为什么需要它：</b>{@link ScriptedMonsterAi} 只包一只 {@link Monster}，
 * 于是 {@link BattleContext#allMonsters()} 只能返回自己、{@link BattleContext#allies()}
 * 恒为空。结果是设计案里已经写好的双怪编队（{@code act1_grubs} 两条蛆、
 * {@code act1_explorers} 探险者二人组）根本没有出场机会，探险者男的
 * {@code Intents.giveAllyBlock} 与 {@code buffAlliesStrength} 在运行时静默失效。</p>
 *
 * <p>本类把 {@link EncounterDefinition#createMonsters()} 产出的整组怪绑定到
 * {@link BattleState} 名册上，并做到三件事：</p>
 *
 * <ol>
 *   <li><b>逐个行动</b>：怪物回合按站位顺序让每只存活怪各自跑完
 *       「开始 → 执行意图 → 结束 → 规划下回合」；</li>
 *   <li><b>真实友方视图</b>：{@code allMonsters()} / {@code allies()} / {@code findAlly()}
 *       返回真实名册，友方技能终于生效；</li>
 *   <li><b>跟随目标</b>：{@link #name()} / {@link #id()} / {@link #intentText} 返回
 *       <b>当前被玩家锁定的那一只</b>，界面高亮与意图显示能跟着选中目标走。</li>
 * </ol>
 *
 * <p>单怪编队（只有一个 spawn）也走这条路径，行为与 {@link ScriptedMonsterAi}
 * 一致，因此流程层可以无脑切换到编队模式。</p>
 */
public final class MonsterEncounterAi implements MonsterAi {

    private final List<Monster> monsters = new ArrayList<>();
    private final String encounterId;
    private final String note;
    private final Random random = new Random();
    private BattleState boundState;

    /**
     * 每个<b>槽位</b>累积的蜕变层数。
     *
     * <p>刻意按槽位记而不是按 {@link Monster} 实例记：蛋「破裂」时会换成新的
     * Monster 实例，层数挂在实例上会当场丢掉。这与 1v1 版本
     * {@link ScriptedMonsterAi} 里 {@code metamorphosisStacks} 不跟蛋实例走的用意一致。</p>
     */
    private final Map<Integer, Integer> metamorphosisBySlot = new HashMap<>();

    /**
     * 按遭遇 id 生成编队。
     *
     * @param encounterId 例如 {@code "act1_grubs"}；需先由图鉴类注册
     */
    public static MonsterEncounterAi ofEncounter(String encounterId) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        EncounterDefinition definition = EncounterCatalog.require(encounterId);
        return new MonsterEncounterAi(
                definition.createMonsters(), definition.id(), definition.note());
    }

    /**
     * 按怪物 id 直接拼一个编队，同一种怪可以重复出现（各自独立实例与血量）。
     *
     * @param monsterIds 怪物定义 id，顺序即站位
     */
    public static MonsterEncounterAi ofIds(String... monsterIds) {
        ActOneBestiary.init();
        ActOneExtraBestiary.init();
        if (monsterIds == null || monsterIds.length == 0) {
            throw new IllegalArgumentException("编队至少需要一只怪物");
        }
        List<Monster> spawns = new ArrayList<>(monsterIds.length);
        for (int i = 0; i < monsterIds.length; i++) {
            String id = monsterIds[i];
            int variant = 0;
            for (int j = 0; j < i; j++) {
                if (monsterIds[j].equals(id)) {
                    variant++;
                }
            }
            spawns.add(new Monster(
                    com.roguelike.dungeon.game.enemy.MonsterCatalog.require(id), variant));
        }
        return new MonsterEncounterAi(spawns, String.join("+", monsterIds), "");
    }

    public MonsterEncounterAi(List<Monster> monsters, String encounterId, String note) {
        if (monsters == null || monsters.isEmpty()) {
            throw new IllegalArgumentException("编队至少需要一只怪物");
        }
        this.monsters.addAll(monsters);
        this.encounterId = encounterId == null ? "" : encounterId;
        this.note = note == null ? "" : note;
    }

    /** 本编队的怪物实例（只读）。 */
    public List<Monster> monsters() {
        return List.copyOf(monsters);
    }

    /** 遭遇 id；直接拼编队时是各 id 用 {@code +} 连接。 */
    public String encounterId() {
        return encounterId;
    }

    /** 遭遇备注（设计案里写的机制说明）。 */
    public String note() {
        return note;
    }

    /** 出场怪物数量。 */
    public int size() {
        return monsters.size();
    }

    /** 存活怪物数量。 */
    public int aliveCount() {
        int alive = 0;
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                alive++;
            }
        }
        return alive;
    }

    /** 当前被玩家锁定的那只怪；未绑定战斗状态时用第一只。 */
    private Monster acting() {
        if (boundState != null) {
            Monster target = boundState.getTarget();
            if (target != null) {
                return target;
            }
        }
        return monsters.get(0);
    }

    @Override
    public String name() {
        return acting().displayName();
    }

    @Override
    public String id() {
        return acting().id();
    }

    @Override
    public int maxHp() {
        return monsters.get(0).getMaxHealth();
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished()) {
            return "已倒下";
        }
        if (state.isEncounterCleared()) {
            return "已倒下";
        }
        String text = acting().intentText();
        return text.isBlank() ? acting().displayName() + "正在观察" : text;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished()) {
            return null;
        }
        return snapshotOf(acting());
    }

    @Override
    public IntentSnapshot intentInfoFor(BattleState state, Monster monster) {
        if (state.isFinished() || monster == null) {
            return null;
        }
        return snapshotOf(monster);
    }

    /** 编队全员的一次性摘要，供战斗日志与调试使用：「两条蛆」「探险者男 + 探险者女」。 */
    public String rosterSummary() {
        List<String> names = new ArrayList<>(monsters.size());
        for (Monster monster : monsters) {
            names.add(monster.displayName());
        }
        return String.join(" + ", names);
    }

    @Override
    public void startFight(BattleState state) {
        this.boundState = state;
        state.bindMonsters(monsters);
        Context ctx = new Context();
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                monster.planIntent(ctx);
            }
        }
        state.syncFromLivingMonster();
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        this.boundState = state;
        state.setPlayerTurn(false);
        Context ctx = new Context();
        List<Monster> snapshot = List.copyOf(monsters);
        boolean attacked = false;
        int blockGained = 0;

        for (Monster monster : snapshot) {
            if (monster.isDead() || state.getPlayer().getHealth() <= 0) {
                continue;
            }
            int slot = snapshot.indexOf(monster);
            Intent planned = monster.plannedIntent();
            int armorBefore = monster.getArmor();

            monster.onTurnStart(ctx);
            monster.takeTurn(ctx);

            // 同场变身（Boss 蛋 → 破壳而出）会把槽位换成新实例，必须重新读一遍
            Monster acting = monsters.contains(monster) ? monster : monsters.get(slot);
            if (acting != monster) {
                // 蛋在自己回合「破裂」：累积一层蜕变并重挂到新形态
                persistMetamorphosisAfterRupture(slot, acting);
                acting.planIntent(ctx);
            } else if (!monster.isDead()) {
                monster.onTurnEnd(ctx);
                monster.planIntent(ctx);
            }

            if (planned != null
                    && (planned.type() == IntentType.ATTACK
                    || planned.type() == IntentType.ATTACK_DEBUFF)) {
                attacked = true;
            }
            blockGained += Math.max(0, acting.getArmor() - armorBefore);
        }

        state.retargetIfNeeded();
        state.syncFromLivingMonster();
        return attacked
                ? MonsterTurnResult.attack(0)
                : MonsterTurnResult.defend(blockGained);
    }

    /**
     * 编队里某只怪血量归零时，若它还有下一形态就在<b>原槽位</b>换成新形态。
     *
     * <p>这是 {@link MonsterAi} 上的钩子：{@link Combat#checkFinished()} 会在判胜之前先问一次，
     * 返回 {@code true} 表示「已变形、战斗继续，不要判胜」。1v1 由 {@link ScriptedMonsterAi}
     * 实现；<b>编队路径必须自己接住</b>——否则带 {@code nextFormId} 的怪一旦进编队池，
     * 血量归零后会直接死亡而不会变形。</p>
     *
     * <p>与 1v1 版本的差异：这里不实现「雇佣兵契约立刻打碎几乎破裂的蛋」
     * （{@code smashAlmostCrackedEgg}）。那是 Boss 单怪的玩法开关，编队没有对应的开关位。</p>
     *
     * @return {@code true} 表示已变形且战斗应继续
     */
    @Override
    public boolean onHpDepleted(BattleState state) {
        this.boundState = state;
        Context ctx = new Context();
        for (int slot = 0; slot < monsters.size(); slot++) {
            Monster monster = monsters.get(slot);
            if (!monster.isDead()) {
                continue;
            }
            String nextFormId = monster.definition().nextFormId();
            if (nextFormId == null || nextFormId.isBlank()) {
                continue;
            }
            MonsterDefinition nextForm =
                    com.roguelike.dungeon.game.enemy.MonsterCatalog.require(nextFormId);
            Monster born = ctx.transform(monster, nextForm);
            if (born.isDead()) {
                // 下一形态血量也不为正（数据异常）：不接管，让正常判胜流程继续
                continue;
            }
            // 玩家打碎蛋：进入下一形态但不算「破裂」，所以不加层，只把已有层数带过去
            applyStoredMetamorphosis(born, metamorphosisBySlot.getOrDefault(slot, 0));
            born.planIntent(ctx);
            state.syncFromLivingMonster();
            return true;
        }
        return false;
    }

    /**
     * 蛋在自己回合「破裂」后累加一层蜕变，并重挂到新形态。
     *
     * <p>与 {@link ScriptedMonsterAi#onHpDepleted} 的分工：玩家提前打死蛋走那条路径（不加层），
     * 蛋自己走到「破裂」意图才走这里（加层）。</p>
     */
    private void persistMetamorphosisAfterRupture(int slot, Monster current) {
        int stacks = metamorphosisBySlot.getOrDefault(slot, 0) + 1;
        metamorphosisBySlot.put(slot, stacks);
        applyStoredMetamorphosis(current, stacks);
    }

    /**
     * 重挂蜕变层数：还有下一形态就继续带着层数走，已是最后形态则折算成力量。
     *
     * <p>语义与 {@link ScriptedMonsterAi} 保持一致——蛋形态只存层数不给力量，
     * 凯洛斯出场时把全部层数一次性转成力量。</p>
     */
    private void applyStoredMetamorphosis(Monster monster, int stacks) {
        if (monster == null) {
            return;
        }
        monster.removeStatus(StatusIds.METAMORPHOSIS);
        if (monster.definition().nextFormId() != null) {
            if (stacks > 0) {
                monster.applyStatus(StatusIds.METAMORPHOSIS, stacks);
            }
        } else {
            monster.setBaseStrength(Math.max(monster.baseStrength(), stacks));
        }
    }

    private static IntentSnapshot snapshotOf(Monster monster) {
        if (monster.isDead()) {
            return null;
        }
        Intent intent = monster.plannedIntent();
        if (intent == null) {
            return null;
        }
        return switch (intent.type()) {
            case ATTACK, ATTACK_DEBUFF -> new IntentSnapshot("ATTACK", 0);
            case DEFEND, DEFEND_BUFF -> new IntentSnapshot("DEFEND", 0);
            default -> new IntentSnapshot(intent.type().name(), 0);
        };
    }

    /**
     * 多怪共用的战斗上下文。
     *
     * <p>与 {@link ScriptedMonsterAi} 的内部类相比，这里的关键差异只有一处：
     * {@code allMonsters()} / {@code allies()} / {@code findAlly()} 接的是
     * {@link BattleState} 的真实名册，而不是「只有自己」。</p>
     */
    private final class Context implements BattleContext {

        @Override
        public Player player() {
            return boundState.getPlayer();
        }

        @Override
        public void damagePlayer(int amount, Object source) {
            boundState.applyDamage(false, amount);
        }

        @Override
        public void healPlayer(int amount) {
            boundState.getPlayer().heal(amount);
        }

        @Override
        public void applyStatusToPlayer(String statusId, int stacks, Object source) {
            StatusEffect effect = toPlayerStatus(statusId);
            if (effect != null) {
                boundState.getPlayer().addStacks(effect, stacks);
            }
        }

        @Override
        public void addCardToPlayerDrawPile(String cardId, int count) {
            Card card;
            try {
                card = CardLibrary.byId(cardId);
            } catch (IllegalArgumentException exception) {
                logEntry("抽牌堆未加入「" + cardId + "」（卡牌尚未实装）");
                return;
            }
            for (int i = 0; i < count; i++) {
                boundState.getPiles().putOnTopOfDrawPile(
                        new CardInstance(UUID.randomUUID().toString(), card));
            }
        }

        @Override
        public List<Monster> allMonsters() {
            return boundState.getMonsters();
        }

        @Override
        public List<Monster> allies(Monster self) {
            List<Monster> allies = new ArrayList<>();
            for (Monster monster : boundState.getMonsters()) {
                if (monster != self && !monster.isDead()) {
                    allies.add(monster);
                }
            }
            return List.copyOf(allies);
        }

        @Override
        public Optional<Monster> findAlly(Monster self, String monsterId) {
            for (Monster monster : boundState.getMonsters()) {
                if (monster != self && !monster.isDead() && monster.id().equals(monsterId)) {
                    return Optional.of(monster);
                }
            }
            return Optional.empty();
        }

        @Override
        public Monster transform(Monster oldForm, MonsterDefinition newForm) {
            Monster born = new Monster(newForm, oldForm.variant());
            int index = monsters.indexOf(oldForm);
            if (index >= 0) {
                monsters.set(index, born);
            } else {
                monsters.add(born);
            }
            return boundState.replaceMonster(oldForm, born);
        }

        @Override
        public void log(String message) {
            logEntry(message);
        }

        @Override
        public Random random() {
            return random;
        }
    }

    /**
     * 脚本内部日志。
     *
     * <p>与 1v1 版本刻意不同：多怪编队的友方技能（给同伴上甲、全体加力量）
     * 只有在有日志时才能被验证是否真的生效，因此这里把 {@code ctx.log}
     * 转发到战斗日志，而不是静默丢弃。</p>
     */
    private void logEntry(String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }

    private java.util.function.Consumer<String> logSink;

    /** 由 {@link Combat} 注入日志出口，让脚本细节能出现在战斗日志里。 */
    public void setLogSink(java.util.function.Consumer<String> logSink) {
        this.logSink = logSink;
    }

    private static StatusEffect toPlayerStatus(String statusId) {
        if (StatusIds.VULNERABLE.equals(statusId)) {
            return StatusEffect.VULNERABLE;
        }
        if (StatusIds.WEAK.equals(statusId)) {
            return StatusEffect.WEAK;
        }
        if (StatusIds.POISON.equals(statusId)) {
            return StatusEffect.POISON;
        }
        return null;
    }
}
