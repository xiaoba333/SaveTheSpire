package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardType;
import com.roguelike.dungeon.game.deck.CardPiles;
import com.roguelike.dungeon.game.entity.BattleInfo;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.entity.StatusEffect;
import com.roguelike.dungeon.game.enemy.DamageContext;
import com.roguelike.dungeon.game.enemy.Monster;
import com.roguelike.dungeon.game.enemy.status.StatusIds;
import com.roguelike.dungeon.game.relic.RelicService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一场战斗的可变状态容器。
 *
 * <p>只保存玩家、怪物、牌堆和回合标记，不依赖 {@link Combat}，
 * 也不包含出牌、怪物 AI 或胜负通知逻辑。</p>
 *
 * <p>同时实现 {@link BattleInfo}，作为遗物可读写的战斗视图。
 * 遗物服务由 {@link Combat} 在装配时注入；独立 Demo 或单元测试下可以为 null，
 * 此时所有遗物钩子自动跳过，行为与引入遗物之前完全一致。</p>
 *
 * <h2>怪物名册与目标选择</h2>
 *
 * <p>本类同时支持两种怪物表示方式：</p>
 *
 * <ol>
 *   <li><b>名册模式（多怪）</b>：{@link #bindMonsters(List)} 绑定一批
 *       {@link Monster} 实体。此时的「当前怪物」由 {@link #getTargetIndex()}
 *       决定——玩家所有单目标操作（伤害、叠状态、上护甲）都落在被选中的那一只身上，
 *       这就是「选择敌人打击」的落点。{@code ActOneBestiary} 里的双怪编队
 *       （两条蛆、探险者二人组）走这条路径。</li>
 *   <li><b>木桩模式（单整数血量）</b>：名册为空时沿用 {@code monsterHp} /
 *       {@code monsterBlock} 两个整数。{@code DefaultMonsterAi} 与大量卡牌单测
 *       依赖这条路径，保持完全不变。</li>
 * </ol>
 *
 * <p>名册模式下，每只怪各自持有一份战斗状态（易伤 / 虚弱 / 中毒 / 血畜等），
 * 按怪物实例用身份比较索引；木桩模式仍用单份状态表。两条路径互不干扰。</p>
 */
public final class BattleState implements BattleInfo {

    private final Player player;
    private final CardPiles piles;
    private final List<CardInstance> battleDeck;
    private int monsterMaxHp;
    private int monsterHp;
    private int monsterBlock;
    /** 脚本怪实体；测试木桩战斗为 null，此时仍用上面的整数血量。 */
    private Monster livingMonster;

    /** 本场出场的怪物名册。为空表示走木桩模式。 */
    private final List<Monster> monsters = new ArrayList<>();
    /** 玩家当前锁定的攻击目标在 {@link #monsters} 中的下标。 */
    private int targetIndex;

    /** 名册模式下每只怪各自的战斗状态。键用身份比较，不依赖 equals。 */
    private final Map<Monster, EnumMap<StatusEffect, Integer>> rosterStatuses =
            new IdentityHashMap<>();
    /** 木桩模式下的单怪状态表（名册为空时使用）。 */
    private final Map<StatusEffect, Integer> monsterStatuses =
            new EnumMap<>(StatusEffect.class);

    /** true 表示怪物下一次行动是攻击，false 表示给自己叠盾。 */
    private boolean monsterWillAttack;
    private boolean playerTurn;
    private boolean finished;
    private String resultText = "";
    private String resultCode;
    private int turnNumber = 1;

    /** 本回合已打出的牌数，供「记账本」这类遗物判断节奏。 */
    private int cardsPlayedThisTurn;
    /** 本场战斗累计打出的牌数。 */
    private int cardsPlayedThisBattle;
    /** 本场战斗累计打出的攻击牌数，供苦无 / 手里剑这类计数遗物使用。 */
    private int attacksPlayedThisBattle;
    /** 本场战斗累计打出的技能牌数。 */
    private int skillsPlayedThisBattle;

    /** 遗物分发器，由 Combat 注入；为 null 时所有遗物钩子跳过。 */
    private RelicService relicService;

    /**
     * @param player 本局共享玩家（生命会保留到后续关卡）
     * @param battleDeck 本场战斗使用的牌组快照
     * @param piles 本场战斗的四类牌堆
     * @param monsterMaxHp 本场怪物生命上限（木桩模式使用）
     */
    public BattleState(
            Player player,
            List<CardInstance> battleDeck,
            CardPiles piles,
            int monsterMaxHp) {
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        this.battleDeck = List.copyOf(Objects.requireNonNull(
                battleDeck, "战斗牌组不能为 null"));
        this.piles = Objects.requireNonNull(piles, "牌堆不能为 null");
        this.monsterMaxHp = monsterMaxHp;
        this.monsterHp = monsterMaxHp;
    }

    public Player getPlayer() {
        return player;
    }

    public CardPiles getPiles() {
        return piles;
    }

    public List<CardInstance> getBattleDeck() {
        return battleDeck;
    }

    public int getMonsterMaxHp() {
        Monster target = selectedMonster();
        return target == null ? monsterMaxHp : target.getMaxHealth();
    }

    public int getMonsterHp() {
        Monster target = selectedMonster();
        return target == null ? monsterHp : target.getHealth();
    }

    public void setMonsterHp(int monsterHp) {
        Monster target = selectedMonster();
        if (target == null) {
            this.monsterHp = monsterHp;
        } else {
            target.setHealth(monsterHp);
        }
    }

    // ---------- 怪物名册与目标选择 ----------

    /**
     * 绑定本场出场的怪物名册，并把攻击目标重置为第一只存活怪。
     *
     * <p>绑定后 {@link #getMonsterHp()} 等单怪访问器改为读取「被选中」的那一只，
     * 玩家出牌造成的伤害也只落在它身上。</p>
     *
     * @param roster 出场怪物，顺序即站位顺序（左 → 右）
     */
    public void bindMonsters(List<Monster> roster) {
        List<Monster> copy = List.copyOf(Objects.requireNonNull(roster, "怪物名册不能为 null"));
        monsters.clear();
        monsters.addAll(copy);
        rosterStatuses.keySet().retainAll(copy);
        targetIndex = Math.max(0, firstAliveIndex());
        livingMonster = copy.isEmpty() ? null : copy.get(0);
        syncFromLivingMonster();
    }

    /**
     * 绑定单只怪物实体（旧入口，等价于 {@code bindMonsters(List.of(monster))}）。
     *
     * <p>保留它是为了让 {@code ScriptedMonsterAi} 的同场变身逻辑继续可用。</p>
     */
    public void bindLivingMonster(Monster monster) {
        if (monster == null) {
            bindMonsters(List.of());
            return;
        }
        bindMonsters(List.of(monster));
    }

    public Monster getLivingMonster() {
        return selectedMonster();
    }

    /** 本场出场的全部怪物（只读，含已死亡的）。 */
    public List<Monster> getMonsters() {
        return Collections.unmodifiableList(monsters);
    }

    /** 本场存活的怪物（只读）。 */
    public List<Monster> getAliveMonsters() {
        List<Monster> alive = new ArrayList<>();
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                alive.add(monster);
            }
        }
        return List.copyOf(alive);
    }

    /** 本场是否走多怪名册模式。 */
    public boolean hasRoster() {
        return !monsters.isEmpty();
    }

    /** 当前锁定的攻击目标下标；名册为空时返回 -1。 */
    public int getTargetIndex() {
        return selectedMonster() == null ? -1 : targetIndex;
    }

    /** 当前锁定的攻击目标；名册为空时返回 null。 */
    public Monster getTarget() {
        return selectedMonster();
    }

    /**
     * 切换攻击目标。
     *
     * <p>只允许选中存活怪物：传入已死亡的下标会被拒绝，避免把伤害打进尸体。</p>
     *
     * @return 是否切换成功
     */
    public boolean selectTarget(int index) {
        if (index < 0 || index >= monsters.size() || monsters.get(index).isDead()) {
            return false;
        }
        targetIndex = index;
        syncFromLivingMonster();
        return true;
    }

    /**
     * 按怪物定义 id 切换攻击目标，选中第一个匹配的存活怪。
     *
     * @return 是否切换成功
     */
    public boolean selectTargetById(String monsterId) {
        if (monsterId == null) {
            return false;
        }
        for (int i = 0; i < monsters.size(); i++) {
            Monster monster = monsters.get(i);
            if (!monster.isDead() && monsterId.equals(monster.id())) {
                return selectTarget(i);
            }
        }
        return false;
    }

    /**
     * 在当前目标已死亡时，自动切到第一只存活怪。
     *
     * <p>玩家打死锁定目标后，后续伤害不会继续打进尸体。</p>
     */
    public void retargetIfNeeded() {
        Monster current = selectedMonster();
        if (current != null && !current.isDead()) {
            return;
        }
        int next = firstAliveIndex();
        if (next >= 0) {
            targetIndex = next;
        }
        syncFromLivingMonster();
    }

    /**
     * 把某只怪替换成新形态，保持它在名册中的槽位不变。
     *
     * <p>Boss「凯洛斯：蛋 → 破壳而出」依赖这个方法；多怪编队里换形态也不会打乱站位。</p>
     *
     * @return 新形态实例
     */
    public Monster replaceMonster(Monster oldForm, Monster newForm) {
        Objects.requireNonNull(newForm, "新形态不能为 null");
        int index = monsters.indexOf(oldForm);
        if (index < 0) {
            return newForm;
        }
        monsters.set(index, newForm);
        EnumMap<StatusEffect, Integer> carried = rosterStatuses.remove(oldForm);
        if (carried != null) {
            rosterStatuses.put(newForm, carried);
        }
        syncFromLivingMonster();
        return newForm;
    }

    /** 把当前目标的血量 / 护甲 / 上限同步回木桩字段，供旧调用方读取。 */
    public void syncFromLivingMonster() {
        Monster target = selectedMonster();
        if (target == null) {
            return;
        }
        livingMonster = target;
        monsterMaxHp = target.getMaxHealth();
        monsterHp = target.getHealth();
        monsterBlock = target.getArmor();
    }

    /** 第一只存活怪的下标；全部阵亡时返回 -1。 */
    public int firstAliveIndex() {
        for (int i = 0; i < monsters.size(); i++) {
            if (!monsters.get(i).isDead()) {
                return i;
            }
        }
        return -1;
    }

    /** 当前锁定目标；名册为空或下标越界时返回 null。 */
    public Monster selectedMonster() {
        if (monsters.isEmpty()) {
            return null;
        }
        if (targetIndex < 0 || targetIndex >= monsters.size()) {
            targetIndex = 0;
        }
        return monsters.get(targetIndex);
    }

    /**
     * 本场敌人是否已被全部清空。
     *
     * <p>胜负判定必须用这个方法，不能用 {@code getMonsterHp() <= 0}：
     * 后者只看被锁定的那一只，打死一只就会误判胜利。</p>
     */
    public boolean isEncounterCleared() {
        if (monsters.isEmpty()) {
            return monsterHp <= 0;
        }
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                return false;
            }
        }
        return true;
    }

    public int getMonsterBlock() {
        Monster target = selectedMonster();
        return target == null ? monsterBlock : target.getArmor();
    }

    /**
     * 设置当前目标护甲。
     *
     * <p>{@link Monster} 只提供「清空」与「累加」两个入口，因此这里只保证
     * {@code 0}（清空）语义精确，其它值按差值累加处理。</p>
     */
    public void setMonsterBlock(int monsterBlock) {
        Monster target = selectedMonster();
        if (target == null) {
            this.monsterBlock = monsterBlock;
            return;
        }
        if (monsterBlock <= 0) {
            target.clearArmor();
            return;
        }
        int delta = monsterBlock - target.getArmor();
        if (delta > 0) {
            target.addArmor(delta);
        }
    }

    public boolean isMonsterWillAttack() {
        return monsterWillAttack;
    }

    public void setMonsterWillAttack(boolean monsterWillAttack) {
        this.monsterWillAttack = monsterWillAttack;
    }

    public boolean isPlayerTurn() {
        return playerTurn;
    }

    public void setPlayerTurn(boolean playerTurn) {
        this.playerTurn = playerTurn;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    // ---------- 遗物接入 ----------

    /** 注入遗物分发器。由 {@link Combat} 在装配战斗时调用。 */
    public void setRelicService(RelicService relicService) {
        this.relicService = relicService;
    }

    /** 分发一次遗物触发；未接入遗物时返回原值。 */
    private int fireRelic(RelicTrigger trigger, int value) {
        if (relicService == null) {
            return value;
        }
        return relicService.fire(trigger, value);
    }

    /** 战斗开始时清空怪物身上的全部状态。 */
    public void clearMonsterStatuses() {
        monsterStatuses.clear();
        rosterStatuses.clear();
    }

    /** 玩家回合开始时清空本回合计数。 */
    public void resetTurnCounters() {
        cardsPlayedThisTurn = 0;
    }

    /** 战斗开始时清空本场累计计数。 */
    public void resetBattleCounters() {
        cardsPlayedThisTurn = 0;
        cardsPlayedThisBattle = 0;
        attacksPlayedThisBattle = 0;
        skillsPlayedThisBattle = 0;
    }

    /**
     * 记录一次成功出牌，同时累加本回合与本场计数。
     *
     * @param type 打出的卡牌类型
     */
    public void onCardPlayed(CardType type) {
        cardsPlayedThisTurn++;
        cardsPlayedThisBattle++;
        if (type == CardType.ATTACK) {
            attacksPlayedThisBattle++;
        } else if (type == CardType.SKILL) {
            skillsPlayedThisBattle++;
        }
    }

    // ---------- BattleInfo：遗物可读的战斗视图 ----------

    @Override
    public int monsterHp() {
        return getMonsterHp();
    }

    @Override
    public int monsterMaxHp() {
        return getMonsterMaxHp();
    }

    @Override
    public int monsterBlock() {
        return getMonsterBlock();
    }

    @Override
    public boolean monsterWillAttack() {
        return monsterWillAttack;
    }

    @Override
    public int turnNumber() {
        return turnNumber;
    }

    @Override
    public int cardsPlayedThisTurn() {
        return cardsPlayedThisTurn;
    }

    @Override
    public int cardsPlayedThisBattle() {
        return cardsPlayedThisBattle;
    }

    @Override
    public int attacksPlayedThisBattle() {
        return attacksPlayedThisBattle;
    }

    @Override
    public int skillsPlayedThisBattle() {
        return skillsPlayedThisBattle;
    }

    // ---------- 怪物状态 ----------

    /** 当前目标身上某种状态的层数。 */
    public int getMonsterStacks(StatusEffect effect) {
        return monsterStacks(effect);
    }

    /** 指定怪物身上某种状态的层数。 */
    public int getMonsterStacks(Monster monster, StatusEffect effect) {
        return statusOf(monster, effect);
    }

    @Override
    public int monsterStacks(StatusEffect effect) {
        return statusOf(selectedMonster(), effect);
    }

    /** 给当前目标叠加状态层数，层数不会低于 0，减到 0 时移除。 */
    @Override
    public void addMonsterStacks(StatusEffect effect, int amount) {
        addMonsterStacks(selectedMonster(), effect, amount);
    }

    /**
     * 给场上<b>全体存活怪物</b>叠加状态层数。
     *
     * <p>开战布局类效果（「麻醉剂」的虚弱、Boss 遗物「毒心」的中毒）用这条路径，
     * 否则多怪编队下只有被锁定的那一只会中招，「削弱这伙敌人」的语义就丢了。
     * 战斗过程中累积类效果仍走单目标版本，尊重玩家的集火选择。</p>
     *
     * <p>木桩模式（名册为空）下退化为给唯一那只怪叠加，与单目标版本完全等价。</p>
     */
    @Override
    public void addAllMonsterStacks(StatusEffect effect, int amount) {
        if (effect == null || amount == 0) {
            return;
        }
        if (!hasRoster()) {
            addMonsterStacks(effect, amount);
            return;
        }
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                addMonsterStacks(monster, effect, amount);
            }
        }
    }

    /**
     * 给指定怪物叠加状态层数。
     *
     * @param monster 目标怪物；传 null 表示走木桩模式的状态表
     */
    public void addMonsterStacks(Monster monster, StatusEffect effect, int amount) {
        if (effect == null || amount == 0) {
            return;
        }
        Map<StatusEffect, Integer> table = monster == null ? monsterStatuses : tableFor(monster);
        int next = Math.max(0, table.getOrDefault(effect, 0) + amount);
        if (next == 0) {
            table.remove(effect);
        } else {
            table.put(effect, next);
        }
    }

    @Override
    public int dealDirectDamageToMonster(int amount) {
        return dealDirectDamageToMonster(selectedMonster(), amount);
    }

    /**
     * 对指定怪物造成真实伤害（无视护甲与状态修正）。
     *
     * @return 实际扣除的血量
     */
    public int dealDirectDamageToMonster(Monster monster, int amount) {
        if (amount <= 0 || monster == null || monster.isDead()) {
            return 0;
        }
        int lost = monster.takeTrueDamage(amount);
        syncFromLivingMonster();
        return lost;
    }

    /**
     * 结算一次伤害。
     *
     * <p>结算顺序：遗物修正 → 目标易伤 → 护甲吸收 → 扣血。
     * 遗物看到的是易伤结算之前的原始伤害。</p>
     *
     * @param toMonster true 表示伤害打向怪物（落在当前锁定目标身上），false 表示打向玩家
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    public int applyDamage(boolean toMonster, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (toMonster) {
            Monster target = selectedMonster();
            if (target != null) {
                return applyDamageToMonster(target, amount);
            }
            return applyDamageToLegacyMonster(amount);
        }

        if (player.hasStatus(StatusEffect.BLOOD_POOL)) {
            return 0;
        }
        int incoming = fireRelic(RelicTrigger.DAMAGE_TAKEN, amount);
        if (incoming <= 0) {
            return 0;
        }
        return player.receiveDamage(incoming);
    }

    /**
     * 对指定怪物结算一次伤害。
     *
     * <p>这是「选择敌人打击」真正的落点：卡牌的单目标伤害最终都汇聚到这里，
     * 由 {@code target} 决定打谁。</p>
     *
     * @param target 受击怪物
     * @param amount 原始伤害值
     * @return 实际扣除的血量
     */
    public int applyDamageToMonster(Monster target, int amount) {
        if (amount <= 0 || target == null || target.isDead()) {
            return 0;
        }
        int calculated = player.calcDealtDamage(amount);
        int modified = fireRelic(RelicTrigger.DAMAGE_DEALT, calculated);
        if (modified <= 0) {
            return 0;
        }
        if (statusOf(target, StatusEffect.VULNERABLE) > 0) {
            modified = modified * 3 / 2;
        }
        DamageContext ctx = new DamageContext(modified, target, true, player);
        int hpLoss = target.receiveDamage(ctx);
        retargetIfNeeded();
        syncFromLivingMonster();
        return hpLoss;
    }

    /**
     * 对场上所有存活怪物各结算一次伤害（群体攻击牌走这里）。
     *
     * <p>每只怪各算一次伤害实例，因此「造成伤害」类遗物会按命中数触发多次。</p>
     *
     * @return 命中的怪物数量
     */
    public int applyDamageToAllMonsters(int amount) {
        int hit = 0;
        for (Monster monster : getAliveMonsters()) {
            applyDamageToMonster(monster, amount);
            hit++;
        }
        return hit;
    }

    /** 给场上所有存活怪物叠加同一种状态。 */
    public int applyStatusToAllMonsters(StatusEffect effect, int amount) {
        int hit = 0;
        for (Monster monster : getAliveMonsters()) {
            addMonsterStacks(monster, effect, amount);
            hit++;
        }
        return hit;
    }

    /** 木桩模式（无怪物实体）的伤害结算。 */
    private int applyDamageToLegacyMonster(int amount) {
        int calculated = player.calcDealtDamage(amount);
        int modified = fireRelic(RelicTrigger.DAMAGE_DEALT, calculated);
        if (modified <= 0) {
            return 0;
        }
        if (monsterStacks(StatusEffect.VULNERABLE) > 0) {
            modified = modified * 3 / 2;
        }
        int absorbed = Math.min(monsterBlock, modified);
        monsterBlock -= absorbed;
        int hpLoss = modified - absorbed;
        monsterHp = Math.max(0, monsterHp - hpLoss);
        return hpLoss;
    }

    /**
     * 给怪增加护甲。amount &lt;= 0 时忽略。
     */
    public void addMonsterBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        Monster target = selectedMonster();
        if (target == null) {
            monsterBlock += amount;
        } else {
            target.addArmor(amount);
        }
    }

    /** 给当前目标叠加指定状态的层数。 */
    public void addMonsterStatus(StatusEffect effect, int amount) {
        if (effect == null) {
            return;
        }
        Monster target = selectedMonster();
        if (target != null && amount > 0 && target.blocksStatus(toStatusId(effect))) {
            return;
        }
        addMonsterStacks(target, effect, amount);
    }

    /** 获取当前目标指定状态的当前层数。 */
    public int getMonsterStatusStacks(StatusEffect effect) {
        return monsterStacks(effect);
    }

    /** 获取指定怪物指定状态的当前层数。 */
    public int getMonsterStatusStacks(Monster monster, StatusEffect effect) {
        return statusOf(monster, effect);
    }

    /** 怪物回合结束状态结算：中毒扣血，易伤、虚弱、血池各减少 1 层。 */
    public void tickMonsterStatuses() {
        if (monsters.isEmpty()) {
            tickStatusesOn(null);
            return;
        }
        for (Monster monster : monsters) {
            if (!monster.isDead()) {
                tickStatusesOn(monster);
            }
        }
    }

    private void tickStatusesOn(Monster monster) {
        int poison = statusOf(monster, StatusEffect.POISON);
        if (poison > 0) {
            if (monster == null) {
                monsterHp = Math.max(0, monsterHp - poison);
            } else {
                monster.takeTrueDamage(poison);
            }
            addMonsterStacks(monster, StatusEffect.POISON, -1);
        }
        addMonsterStacks(monster, StatusEffect.VULNERABLE, -1);
        addMonsterStacks(monster, StatusEffect.WEAK, -1);
        addMonsterStacks(monster, StatusEffect.BLOOD_POOL, -1);
    }

    /** 读某只怪（或木桩）的状态层数。 */
    private int statusOf(Monster monster, StatusEffect effect) {
        if (effect == null) {
            return 0;
        }
        if (monster == null) {
            return monsterStatuses.getOrDefault(effect, 0);
        }
        return tableFor(monster).getOrDefault(effect, 0);
    }

    /** 取某只怪的状态表，不存在则按需创建。 */
    private Map<StatusEffect, Integer> tableFor(Monster monster) {
        return rosterStatuses.computeIfAbsent(
                monster, key -> new EnumMap<>(StatusEffect.class));
    }

    /** 汇总全场存活怪物某种状态的层数，供「血畜」这类收尾结算使用。 */
    public int totalMonsterStacks(StatusEffect effect) {
        if (monsters.isEmpty()) {
            return monsterStatuses.getOrDefault(effect, 0);
        }
        int total = 0;
        for (Monster monster : monsters) {
            total += tableFor(monster).getOrDefault(effect, 0);
        }
        return total;
    }

    private static String toStatusId(StatusEffect effect) {
        return switch (effect) {
            case VULNERABLE -> StatusIds.VULNERABLE;
            case WEAK -> StatusIds.WEAK;
            case POISON -> StatusIds.POISON;
            default -> effect.name().toLowerCase();
        };
    }
}
