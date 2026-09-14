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
import com.roguelike.dungeon.game.enemy.intent.Intent;
import com.roguelike.dungeon.game.enemy.intent.IntentType;
import com.roguelike.dungeon.game.enemy.status.StatusIds;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 把第一章图鉴里的脚本怪接到现有 1v1 {@link Combat}。
 *
 * <p>普通战随机一只小怪；精英是巨人遗骸；Boss 从凯洛斯的蛋开始，破裂后变形成下一形态。</p>
 */
public final class ScriptedMonsterAi implements MonsterAi {

    private static final String ALMOST_CRACKED_EGG_ID = "kairos_egg_4";

    private Monster monster;
    private BattleState boundState;
    private final Random random = new Random();
    /** 蛋链累积的蜕变层数，不跟蛋实例走，避免破裂死亡把状态清掉。 */
    private int metamorphosisStacks;
    /** 雇佣兵契约：进入几乎破裂阶段时立刻打碎，不单独出场雇佣兵。 */
    private final boolean smashAlmostCrackedEgg;

    public static ScriptedMonsterAi of(String monsterId) {
        return of(monsterId, false);
    }

    public static ScriptedMonsterAi of(String monsterId, boolean smashAlmostCrackedEgg) {
        ActOneBestiary.init();
        return new ScriptedMonsterAi(
                new Monster(com.roguelike.dungeon.game.enemy.MonsterCatalog.require(monsterId)),
                smashAlmostCrackedEgg);
    }

    public ScriptedMonsterAi(Monster monster) {
        this(monster, false);
    }

    public ScriptedMonsterAi(Monster monster, boolean smashAlmostCrackedEgg) {
        this.monster = monster;
        this.smashAlmostCrackedEgg = smashAlmostCrackedEgg;
    }

    public Monster monster() {
        return monster;
    }

    @Override
    public String name() {
        return monster.displayName();
    }

    /** 怪物英文标识，前端按此选择敌人视觉。 */
    @Override
    public String id() {
        return monster.id();
    }

    @Override
    public int maxHp() {
        return monster.getMaxHealth();
    }

    @Override
    public String intentText(BattleState state) {
        if (state.isFinished() || monster.isDead()) {
            return "已倒下";
        }
        String text = monster.intentText();
        return text.isBlank() ? monster.displayName() + "正在观察" : text;
    }

    @Override
    public IntentSnapshot intentInfo(BattleState state) {
        if (state.isFinished() || monster.isDead()) {
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

    @Override
    public void startFight(BattleState state) {
        this.boundState = state;
        state.bindLivingMonster(monster);
        monster.planIntent(new Context());
        state.syncFromLivingMonster();
    }

    @Override
    public MonsterTurnResult takeTurn(BattleState state) {
        this.boundState = state;
        state.setPlayerTurn(false);
        Context ctx = new Context();
        Intent current = monster.plannedIntent();
        Monster acting = monster;
        acting.onTurnStart(ctx);
        acting.takeTurn(ctx);
        if (monster != acting) {
            persistMetamorphosisAfterRupture();
            smashAlmostCrackedEggIfHired(ctx);
            monster.planIntent(ctx);
        } else if (!monster.isDead()) {
            monster.onTurnEnd(ctx);
            monster.planIntent(ctx);
        }
        state.bindLivingMonster(monster);
        state.syncFromLivingMonster();

        boolean attacked = current != null
                && (current.type() == IntentType.ATTACK
                || current.type() == IntentType.ATTACK_DEBUFF);
        return attacked
                ? MonsterTurnResult.attack(0)
                : MonsterTurnResult.defend(monster.getArmor());
    }

    @Override
    public boolean onHpDepleted(BattleState state) {
        this.boundState = state;
        if (monster == null || !monster.isDead()) {
            return false;
        }
        String nextFormId = monster.definition().nextFormId();
        if (nextFormId == null || nextFormId.isBlank()) {
            return false;
        }
        Context ctx = new Context();
        // 玩家打碎蛋：进入下一形态，但不算破裂，蜕变不加层。
        if (!advanceToForm(ctx, nextFormId)) {
            return false;
        }
        smashAlmostCrackedEggIfHired(ctx);
        monster.planIntent(ctx);
        state.bindLivingMonster(monster);
        state.syncFromLivingMonster();
        return true;
    }

    /**
     * 蛋自己回合「破裂」后累加蜕变。玩家打碎或雇佣兵砸碎都不要走这里。
     */
    private void persistMetamorphosisAfterRupture() {
        metamorphosisStacks++;
        applyStoredMetamorphosis();
    }

    private void applyStoredMetamorphosis() {
        monster.removeStatus(StatusIds.METAMORPHOSIS);
        if (monster.definition().nextFormId() != null) {
            if (metamorphosisStacks > 0) {
                monster.applyStatus(StatusIds.METAMORPHOSIS, metamorphosisStacks);
            }
        } else {
            monster.setBaseStrength(Math.max(monster.baseStrength(), metamorphosisStacks));
        }
    }

    /** 雇佣兵契约：几乎破裂一出场就立刻打碎。不算破裂，蜕变不加层。 */
    private void smashAlmostCrackedEggIfHired(Context ctx) {
        if (!smashAlmostCrackedEgg || !ALMOST_CRACKED_EGG_ID.equals(monster.id())) {
            return;
        }
        String nextFormId = monster.definition().nextFormId();
        if (nextFormId == null || nextFormId.isBlank()) {
            return;
        }
        advanceToForm(ctx, nextFormId);
    }

    /** 换成下一形态并重挂已有蜕变，不加层。 */
    private boolean advanceToForm(Context ctx, String nextFormId) {
        MonsterDefinition nextForm =
                com.roguelike.dungeon.game.enemy.MonsterCatalog.require(nextFormId);
        monster = ctx.transform(monster, nextForm);
        if (monster.isDead()) {
            return false;
        }
        applyStoredMetamorphosis();
        return true;
    }

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
                log("  抽牌堆未加入「" + cardId + "」（卡牌尚未实装）");
                return;
            }
            for (int i = 0; i < count; i++) {
                boundState.getPiles().putOnTopOfDrawPile(
                        new CardInstance(UUID.randomUUID().toString(), card));
            }
        }

        @Override
        public List<Monster> allMonsters() {
            return List.of(monster);
        }

        @Override
        public List<Monster> allies(Monster self) {
            return List.of();
        }

        @Override
        public Optional<Monster> findAlly(Monster self, String monsterId) {
            return Optional.empty();
        }

        @Override
        public Monster transform(Monster oldForm, MonsterDefinition newForm) {
            Monster born = new Monster(newForm);
            monster = born;
            boundState.bindLivingMonster(born);
            return born;
        }

        @Override
        public void log(String message) {
            // Combat 自己会写回合摘要；脚本细节先静默，避免刷屏。
        }

        @Override
        public Random random() {
            return random;
        }
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
