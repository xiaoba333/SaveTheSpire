# 《Save The Spire》怪物 AI 框架说明

> 版本：v1.0（第一层「地牢外围」）
> 数据来源：`D:\A\怪物ai设计案.docx`
> 代码路径：`src/main/java/com/roguelike/dungeon/game/enemy`
> 技术基线：Java 21 + Maven（与《实体接口规范》一致）
> 状态：框架 + 第一层全部怪物数据已完成，可编译、可运行、可通过 Demo 验证

---

## 一句话结论

设计案里的每一行怪物描述，都被拆成 **四层** 分别落位：

```text
设计案文字  →  数据（MonsterDefinition）  →  脚本（EnemyScript）  →  意图（Intent）  →  行动（EnemyAction）
「骷髅35血」    血量 / 立绘 / 初始状态        1-2循环              打12 / 回6         扣血 / 加护甲
```

**新增一只怪物只需要写数据，不需要改框架**。反过来，改框架也不会碰坏任何一只已有怪物。

---

## 一、分层结构

```text
┌──────────────────────────────────────────────────────────────┐
│  bestiary / ActOneBestiary                                   │  设计案数值的唯一出处
│    「骷髅：35血，初始骨质疏松，1-2循环：打12 / 回6」              │
└──────────────────────────┬───────────────────────────────────┘
                           │ 注册
┌──────────────────────────▼───────────────────────────────────┐
│  MonsterCatalog  /  EncounterCatalog                         │  按 ID 取怪 / 取遭遇
└──────────────────────────┬───────────────────────────────────┘
                           │ new Monster(def, variant)
┌──────────────────────────▼───────────────────────────────────┐
│  Monster  extends  Enemy                                     │  血量+护甲（复用实体层）
│   ├─ statuses : List<StatusEffect>      状态钩子驱动            │  力量 / 立绘 / 标签
│   └─ brain    : EnemyBrain                                  │
│         └─ script : EnemyScript                              │  ← 只换这一层 = 换行为
│               └─ Intent  →  EnemyAction                      │  ← 只换这一层 = 换招式
└──────────────────────────┬───────────────────────────────────┘
                           │ 所有对外效果
┌──────────────────────────▼───────────────────────────────────┐
│  BattleContext                                               │  AI → 战斗模块的唯一口岸
│  （由战斗负责人在 CombatController 中实现）                     │
└──────────────────────────────────────────────────────────────┘
```

**关键设计：AI 不依赖战斗系统，战斗系统也不依赖 AI。**
两边只认识 `BattleContext` 这一个接口，因此 AI 框架可以脱离 JavaFX、脱离卡牌、脱离牌堆单独编译和测试
（`EnemyAiDemo` 就是证明：一个纯命令行 `main`，跑通全部六场遭遇）。

---

## 二、类职责速查

| 包 | 类 | 职责 |
|---|---|---|
| `enemy` | `Monster` | 怪物实体。继承 `Enemy`，加上状态、力量、立绘、大脑 |
| `enemy` | `MonsterDefinition` | 一只怪的设计案数据（不可变），可反复实例化 |
| `enemy` | `MonsterCatalog` | 怪物 ID → 定义 的登记处 |
| `enemy` | `MonsterSprite` | 立绘路径解析（`spriteKey` → `/monster/xxx.png`） |
| `enemy` | `BattleContext` | AI 对外的唯一出口（战斗负责人实现它） |
| `enemy` | `DamageContext` | 一次伤害的管道载体，供状态修改伤害 |
| `enemy` | `EnemyBrain` | 把「决策」和「执行」串起来的运行时 |
| `enemy.intent` | `Intent` / `IntentType` | 一个回合的意图：显示什么 + 做什么 |
| `enemy.intent` | `EnemyAction` | 真正执行的那一层 |
| `enemy.intent` | `Intents` | **领域语言**：`attack(6)`、`blockAndStrength(6,2)`…… |
| `enemy.script` | `EnemyScript` | 决策脚本接口：回答「下回合干什么」 |
| `enemy.script` | `LoopScript` | 固定循环 / 前 N 个只走一次 / 交错 |
| `enemy.script` | `PhaseScript` | 血量阈值分阶段（巨人遗骸） |
| `enemy.status` | `StatusEffect` | 状态基类，一组生命周期钩子 |
| `enemy.status` | `StatusRegistry` | 状态 ID → 实现 的登记处 |
| `enemy.status` | `CreepingStatus` 等 5 个 | 蠕动 / 骨质疏松 / 无灵 / 复苏 / 蜕变 |
| `enemy.encounter` | `EncounterDefinition` | 一次遭遇的编队（出哪几只怪） |
| `enemy.encounter` | `EncounterCatalog` | 按「层数 + 档位」抽敌人 |
| `enemy.bestiary` | `ActOneBestiary` | 第一层全部数据 |
| `enemy.demo` | `EnemyAiDemo` | 命令行演示 + `BattleContext` 参照实现 |

---

## 三、一个回合是怎么跑的

```text
战斗开始
  └─ Monster.planIntent(ctx)          ← 确定意图，UI 立刻显示在怪物头顶

【玩家回合】出牌、结算

【怪物回合】
  ├─ Monster.onTurnStart(ctx)         ← 清空上回合护甲
  │                                    └─ 状态钩子 onTurnStart
  ├─ Monster.takeTurn(ctx)            ← 执行 plannedIntent
  │    └─ 按 actionRepeatMultiplier() 重复（「复苏」= 2 次）
  │         ├─ EnemyAction.perform(self, ctx)
  │         └─ 状态钩子 onActed       ← 「骨质疏松」在这里掉 2 点血
  ├─ Monster.onTurnEnd(ctx)           ← 状态钩子 onTurnEnd + 衰减
  └─ Monster.planIntent(ctx)          ← 确定下回合意图
```

**为什么「定意图」和「执行意图」要分开**：杀戮尖塔类玩法要求玩家在自己回合就能看见
怪物下回合要干什么，才能做规划。所以意图必须先于行动确定，且确定后不再变化。

伤害的完整管道（全部伤害都必须走 `receiveDamage`）：

```text
receiveDamage(DamageContext)
  → 状态 modifyIncomingDamage     ← 蠕动减半 / 骨质疏松 +2
  → 护甲 absorb
  → 血量 takeDamage
  → 状态 onDamaged                ← 蠕动在这里消失
```

> 例外：`Monster.takeTrueDamage(int)` 是**真实伤害**，无视护甲、也**不经过状态修正**。
> 「骨质疏松」每次行动的 2 点自伤走的就是它——否则会被自己那条「受到伤害额外 +2」反复放大。

---

## 四、战斗模块要怎么接进来

### 4.1 实现 `BattleContext`

这是战斗负责人唯一需要做的事。`EnemyAiDemo.SimpleBattleContext` 是一份可直接照抄的最小实现。
必须遵守：

| 方法 | 约定 |
|---|---|
| `damagePlayer(amount, source)` | 必须走「护甲 → 血量」完整结算 |
| `applyStatusToPlayer(id, stacks, source)` | AI 只负责施加，数值结算归玩家状态模块 |
| `allMonsters()` | 返回只读视图，AI 不得借此增删怪物 |
| `transform(old, newDef)` | 在**同一个出场槽位**替换，Boss 孵化靠它 |
| `random()` | 用 `RunState.runSeed` 派生，保证同种子可复现 |

### 4.2 按节点类型抽敌人

`EncounterCategory` 刻意没有复用地图模块的 `MapNodeType`，这样本模块能独立编译。
战斗模块在装配时做一次显式映射即可：

```java
public CombatController(RunState runState, MapNode node, Consumer<CombatResult> finishHandler) {
    ActOneBestiary.init();                       // 幂等，启动时调一次就够

    EncounterCategory category = switch (node.type()) {
        case BATTLE -> EncounterCategory.NORMAL;
        case ELITE  -> EncounterCategory.ELITE;
        case BOSS   -> EncounterCategory.BOSS;
        default     -> throw new IllegalStateException("非战斗节点：" + node.type());
    };

    EncounterDefinition encounter = EncounterCatalog
            .random(category, runState.getCurrentAct(), new Random(runState.getRunSeed()))
            .orElseThrow(() -> new IllegalStateException("第 " + runState.getCurrentAct()
                    + " 层没有配置 " + category + " 遭遇"));

    this.monsters = encounter.createMonsters();
    this.battleContext = new CombatBattleContext(runState.player(), monsters, ...);
    // 之后每个怪物回合前调用 monster.planIntent(ctx)，回合中调用 monster.takeTurn(ctx)
}
```

这样《地图与关卡模块协作说明》里点名要补的三件事就都齐了：
**按 `node.type()` 区分普通/精英/Boss、不再复用同一份怪物数据、`CombatController.getView()` 有稳定的数据来源。**

---

## 五、怎么加一只新怪（三步）

```java
// 第一步：在 ActOneBestiary 里写数据
public static final MonsterDefinition BAT = MonsterDefinition
        .builder("bat", "洞穴蝙蝠", 18)                 // id / 中文名 / 血量
        .sprite("act1/bat")                             // 立绘键（照片文件名）
        .status(StatusIds.WEAK, 0)                      // 初始状态（不需要就删掉这行）
        .script(variant -> Scripts.loop(                // 意图循环
                Intents.attack(5, 2),                   // 打5*2
                Intents.debuffPlayer(StatusIds.WEAK, 1)))
        .tag("act1", "normal", "vermin")                // 分类标签
        .build();

// 第二步：加进 allMonsters()
// 第三步：注册一组遭遇
EncounterCatalog.register(new EncounterDefinition(
        "act1_bats", EncounterCategory.NORMAL, ACT,
        List.of(MonsterSpawn.of("bat"), MonsterSpawn.of("bat")),
        "两只蝙蝠"));
```

**框架覆盖不到的招式**（例如"偷走玩家一张牌"），不要改 `Intents`，用扩展出口：

```java
Intents.custom(IntentType.SPECIAL, "偷走一张手牌", "intent_special",
        (self, ctx) -> ctx.stealRandomCardFromHand());
```

---

## 六、立绘（照片）怎么放

框架已经把位置留好了。规则只有一条：**文件名 = `spriteKey` + `.png`**。

```text
src/main/resources/monster/
├── placeholder.png             ← 兜底图（已生成，可直接替换成正式版）
└── act1/
    ├── grub.png                蛆（设计案里的「两条蛆」，出场两只共用这一张）
    ├── wraith.png              亡灵
    ├── skeleton.png            骷髅
    ├── explorer_male.png       探险者男
    ├── explorer_female.png     探险者女
    ├── giant_remains.png       巨人遗骸
    ├── kairos_egg_1.png        凯洛斯的蛋（无暇）
    ├── kairos_egg_2.png        凯洛斯的蛋（轻微破裂）
    ├── kairos_egg_3.png        凯洛斯的蛋（中度破裂）
    ├── kairos_egg_4.png        凯洛斯的蛋（几乎破裂）
    └── kairos.png              凯洛斯
```

UI 侧加载立绘请统一走：

```java
String path = monster.spriteOrDefault();     // 缺图自动回落到 placeholder.png
Image image = new Image(MonsterSprite.class.getResourceAsStream(path));
```

**照片还没放进去也不会崩**——`spriteOrDefault()` 会自动回落。等你把图丢进上面的目录，代码一行都不用改。

> 注意：`Intent` 里的 `iconKey`（`intent_attack`、`intent_debuff` 等）是**意图小图标**，
> 不是立绘，两套资源互不影响。

---

## 七、第一层已录数据总表

| 怪物 | 血量 | 初始状态 | 意图序列 | 循环 |
|---|---:|---|---|---|
| 蛆 | 12 | 蠕动 1 | 打6 ／ 给予玩家1层虚弱（**交错**：0号从打6起手，1号从上虚弱起手） | 交替 |
| 亡灵 | 20 | — | ① 给予玩家99层易伤 → ② 打6 → ③ 防6并获得2力量 | 2-3 |
| 骷髅 | 35 | 骨质疏松 1 | ① 打12 → ② 回6 | 1-2 |
| 探险者男 | 30 | — | 给女探险者上10甲 ／ 打6 ／ 打12 | 1-2-3 ※ |
| 探险者女 | 20 | — | 打2*3 ／ 两人都加一力量 ／ 给两人回6血 | 1-2-3 ※ |
| 巨人遗骸 | 75 | 无灵 1 | ① 打6并给予1层易伤 → ② 打12 | 1-2 |
| 巨人遗骸·复苏 | — | 复苏 1（无灵消失） | ① 回复10血 → ② 打6并给予1层易伤 → ③ 打12 | 2-3 |
| 凯洛斯的蛋（无暇） | 10 | — | 破裂（杀死自身），+1层蜕变 | — |
| 凯洛斯的蛋（轻微破裂） | 20 | 继承蜕变 | 同上 | — |
| 凯洛斯的蛋（中度破裂） | 30 | 继承蜕变 | 同上 | — |
| 凯洛斯的蛋（几乎破裂） | 40 | 继承蜕变 | 同上 | — |
| 凯洛斯 | 100 | 蜕变 4（力量+4） | 打0*9 ／ 向玩家抽牌堆加1张甲片 ／ 打12 | 1-2-3 ※ |

**遭遇编队**

| 遭遇 ID | 档位 | 编队 |
|---|---|---|
| `act1_grubs` | 普通 | 蛆 ×2（交错） |
| `act1_wraith` | 普通 | 亡灵 |
| `act1_skeleton` | 普通 | 骷髅 |
| `act1_explorers` | 普通 | 探险者男 + 探险者女 |
| `act1_giant_remains` | 精英 | 巨人遗骸 |
| `act1_kairos` | Boss | 凯洛斯的蛋（无暇）→ … → 凯洛斯 |

> ※ = 设计案未标注循环范围，按「全序列循环」实现，见下方待确认清单。

---

## 八、待确认清单（请策划/负责人过一遍）

框架按设计案**原文**录入，以下几处存在歧义，改起来都只需要动 `ActOneBestiary` 的一行：

1. **亡灵「给予玩家99层易伤」** —— 99 层几乎等于直接斩杀玩家，疑似笔误（1 层？9 层？）。
2. **探险者男/女的循环范围** —— 设计案只列了三条意图，没写循环。目前按「1-2-3 循环」实现，
   即男每三回合重新给女补一次 10 甲。
3. **凯洛斯的循环范围** —— 同上，按「1-2-3 循环」实现。
4. **蛋是「四段形态」还是「四只独立的蛋」** —— 当前按**四段形态链**实现
   （无暇 → 轻微破裂 → 中度破裂 → 几乎破裂 → 破壳而出）。hp 从 10 递增到 40，
   也就是蛋越裂越硬，请确认这是有意为之。
5. **「复苏：所有行动判定两次」** —— 当前实现为「整条意图连续执行两遍」。
   副作用是「回复10血」也会回两次（实际测试中是回 20 血）。
   若本意是「多段攻击段数翻倍」，只改 `RevivalStatus` 一个类即可。
6. **「骨质疏松」的 2 点行动自伤是否穿透护甲** —— 当前按**真实伤害**处理（穿甲、且不吃自己的 +2 加成）。
7. **「蠕动」与多段攻击** —— 当前实现为「第一次受到的攻击伤害减半，随后状态消失」；
   若玩家打出多段攻击，只有第一段减半。

---

## 九、跑起来看看

不需要 JavaFX，不需要战斗界面：

```bash
# Maven
mvn -q compile exec:java -Dexec.mainClass=com.roguelike.dungeon.game.enemy.demo.EnemyAiDemo

# 或者在 IDEA 里直接运行 EnemyAiDemo.main
```

输出是完整的战斗日志，例如精英怪进入第二阶段时：

```text
---------- 第 6 回合 ----------
【阶段变化】巨人遗骸 的血量跌落 20，无灵状态消失，获得状态「复苏」
  巨人遗骸 失去了状态「无灵」
  巨人遗骸 获得了状态「复苏」
  [意图] 巨人遗骸：回10
  巨人遗骸 处于「复苏」，本回合行动判定 2 次
  巨人遗骸 回复 10 点生命（当前 25/75）
  巨人遗骸 回复 10 点生命（当前 35/75）
```

Boss 孵化链：

```text
  凯洛斯的蛋（无暇） 破裂了！
  凯洛斯的蛋（轻微破裂） 出现（20/20，力量 1）
  …（四段之后）…
  凯洛斯 出现（100/100，力量 4）
  [意图] 凯洛斯：打0*9（0伤害9次）
  凯洛斯 攻击玩家 4 点（含力量 4）    ← 9 段各 4 点，全部来自蜕变带来的力量
```

---

## 十、扩展点（预留，未实现）

- **多段脚本 / 随机意图表**：目前只有 `LoopScript`（确定循环）与 `PhaseScript`（血量分阶段）。
  需要"三选一随机"时新增一个 `EnemyScript` 实现即可，`Intents.random(...)` 已经预留了命名空间。
- **难度系数**：`MonsterDefinition` 加一个 `scalingFactor` 字段即可实现对第 N 层怪物的血量缩放。
- **第二层**：新建 `bestiary/ActTwoBestiary.java`，复刻 `ActOneBestiary` 的结构，
  `EncounterCatalog.random(category, 2, random)` 自动就能抽到。
- **玩家侧状态**：易伤 / 虚弱 / 中毒的具体数值结算属于玩家状态模块，
  本框架只通过 `BattleContext.applyStatusToPlayer` 上报层数。

---

## 附：与现有模块的边界

| 模块 | 交界点 | 说明 |
|---|---|---|
| 实体层（`IHealth` / `IArmor`） | `Monster extends Enemy` | 血量与护甲**完全复用**，未改动任何实体层代码 |
| 地图模块 | `EncounterCategory` ↔ `MapNodeType` | 显式映射一次，不产生代码依赖 |
| 卡牌模块 | `Intents.addCardToPlayerDrawPile(cardId, name)` | 甲片卡 ID 为 `scale_shard`，卡牌定义由卡牌负责人维护 |
| 战斗模块 | `BattleContext` | 由战斗负责人实现，是本框架唯一的外部依赖面 |
| UI 模块 | `monster.spriteOrDefault()` / `monster.intentText()` | UI 只读这两个方法就能画出怪物区与意图 |

**本框架没有改动 `Player`、`Enemy`、`IHealth`、`IArmor`、`IEnergy`、`App`、`Launcher` 中的任何一行代码。**
