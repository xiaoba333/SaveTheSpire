# Boss 遗物池设计案

> **范围**：为 `game/relic` 建立独立 Boss 遗物池，并打通「Boss 战胜利 → 三选一 → 实际获得 → 下一章生效」的完整路径。
> **参照**：杀戮尖塔（Slay the Spire）的 Boss 遗物规则。
> **数值基准（实测自代码，非臆测）**：玩家 50 生命 / 3 能量；打击 6 伤、防御 6 甲；第一章 Boss 凯洛斯 = 蛋链 10+20+30+40 → 本体 100，**合计 200 血**；精英「巨人遗骸」。
> **落地文件**：`src/main/java/com/roguelike/dungeon/game/relic/`。

---

## 〇、先说结论

按优先级三条，**顺序很重要**：

**1）池子已经有 6 件，但一件都拿不到。**
`bossChoices()` 全项目**零调用**；`relicRaritiesFor()` 不含 `BOSS`；`finishBattle()` 的 BOSS 分支直接 `return`。所以当前真正的缺口不是"遗物不够多"，而是**产出路径断在三个地方**。不接管线，再加 6 件也只是多 6 件死代码。

**2）默认章节数是 1，Boss 遗物在结构上就没有用武之地。**
`FlowApp:66`、`GameServer:51`、`GameFlowDebugMain:40` 三处都是 `DEFAULT_ACT_COUNT = 1`。打完 Boss 就是通关 —— 此时发下来的遗物没有任何后续步骤去消耗它。**Boss 遗物池成立的前提是 `totalActs ≥ 2`**（打完第 N 章 Boss 拿、第 N+1 章用）。

**3）现有 Boss 遗物强度倒挂：整体不如稀有。**
`greedy_cup`（**稀有**）＝ 能量上限 +1 / 最大生命 −8；`dark_pact`（**Boss**）＝ 能量上限 +1 / 最大生命 −12。同样的收益、更重的代价、更高的稀有度 —— 稀有度曲线是反的。

**因此本设计案的主线是：接管线 > 定规模 > 上修数值 > 再补新遗物。**

---

## 一、现状核查（自查证据）

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `relicRarity.BOSS` 枚举存在 | ✅ | `RelicRarity.java:14` |
| Boss 遗物已注册 | ✅ 6 件 | `RelicLibrary.java:422-503` |
| `bossChoices()` 已实现 | ✅ | `RelicLibrary.java:621-637` |
| `bossChoices()` 被调用 | ❌ **零调用** | 全项目 grep 仅命中定义处 |
| Boss 进入常规掉落池 | ❌ | `relicRaritiesFor()` 只给 `{COMMON,UNCOMMON}` 与 `{COMMON,UNCOMMON,RARE}`，`GameController.java:344-349` |
| Boss 节点发奖 | ❌ | `finishBattle()` BOSS 分支 `completeCurrentNode()` → `advanceAct/VICTORY` → `return`，**无奖励**（`GameController.java:309-319`） |
| 奖励载体支持三选一 | ❌ | `BattleReward` 只有单个 `relic` 字段（`BattleReward.java:18`） |
| 前端能看到遗物奖励 | ❌ | `GameStateJson.rewardJson()` 只下发 `gold` + `cardChoices`，**连普通遗物都没下发**（`GameStateJson.java:151-164`） |

**结论**：这 6 件 Boss 遗物是目前项目里最大的一块死代码。

---

## 二、杀戮尖塔的规则，我们照搬哪几条

| StS 规则 | 本项目 | 说明 |
| --- | --- | --- |
| Boss 遗物独立成池，不进常规掉落 | ✅ 照搬 | `RelicRarity.BOSS` 已存在，`randomReward` 不传 BOSS 即可 |
| 打完每章 Boss 后三选一 | ✅ 照搬 | `bossChoices(seed, player, 3)` 已实现 |
| **每件都有明确代价** | ✅ 照搬 | 这是让三选一成为"真选择"的核心，见第四节 |
| 一局可拿多件（每章一件） | ✅ 照搬 | `bossChoices` 已排除已持有 |
| **代价落在另一种资源上** | ✅ 照搬 | 能量 / 生命 / 输出 / 承伤 / 节奏 —— 任意两件都不是"强弱"，而是"打法不同" |
| 手牌上限、抽牌数、金币、药水类代价 | ❌ 放弃 | 项目没有这些系统或钩子，见第三节 |
| 角色专属 Boss 遗物 | ⏸ 暂缓 | 项目已有 `CharacterDefinition`，可后续按角色分池 |

---

## 三、能力边界：能做 / 不能做

**可用机制**：9 个触发点（`OBTAIN / BATTLE_START / TURN_START / TURN_END / CARD_PLAYED / DAMAGE_DEALT / DAMAGE_TAKEN / ENEMY_KILLED / BATTLE_END`）＋ `Relic.modifyCost()` ＋ `Player` 的公开 API。

### 3.1 关键时序（已逐行核实，决定了很多设计能不能成立）

```
startNewFight()                       # Combat.java:481
  resetForBattle()                    # :483  ← 清状态、清能力、能量回满
  clearMonsterStatuses()              # :488
  beginPlayerTurn()
      clearArmor()  refresh()         # :516-517
      triggerTurnStart()  resetTurnCounters()
      fireRelic(TURN_START)           # :522  ← 护甲已清、能量已满、回合计数已归零
      drawToHandSize(HAND_SIZE)       # :523
  fireRelic(BATTLE_START)             # :508  ← 在抽牌之后
  checkFinished()

endPlayerTurn()
  fireRelic(TURN_END)                 # :453  ← 手牌未弃、护甲与剩余能量可读
  discardHand()                       # :455
```

由此得到三条硬结论：

- **`BATTLE_START` 在 `resetForBattle()` 之后** → 在此处给玩家力量/易伤不会被清掉，「开战给buff」类设计**可做**。
- **`TURN_START` 在 `refresh()` 之后** → 此时能量已等于上限，`addEnergy` 又被 clamp 到上限，**「每回合 +1 能量」做不了**（「永久提升能量上限」只能挂在 `OBTAIN`）。
- **`TURN_END` 在弃牌前、敌方回合前** → 在这里加的护甲能撑过怪物回合，在这里读 `getEnergy()` 也拿得到真实剩余值。

### 3.2 做不了 / 需要先改 API

| 想做 | 卡在哪 | 需要的改动 | 预估 |
| --- | --- | --- | --- |
| 每回合 +1 能量（仅本回合可用） | `TURN_START` 时能量已满；`addEnergy` clamp 到上限 | `Player.gainBonusEnergy(int)`（允许破上限） | ~5 行 |
| **能量上限 −1**（以能量换收益） | `Player.addMaxEnergy` 对 `amount <= 0` 直接 return（`Player.java:130-136`） | `Player.reduceMaxEnergy(int)` | ~6 行 |
| **全体怪物叠状态**（开战全体中毒等） | `BattleInfo.addMonsterStacks` 只作用于 `selectedMonster()`（`BattleState.java:526-528`） | 扩展 `BattleInfo`：`addAllMonsterStacks(...)` / `monsterCount()` | ~15 行 + 实现 |
| 金币类代价 | `RelicContext` 拿不到 `RunState`（只有 player / battle / trigger / value / card / depth / logger） | 给 `RelicContext` 注入 `RunState` | ~10 行 + 波及 |
| 抽牌数 / 手牌上限 | 无对应触发点 | 新增 `RelicTrigger.CARD_DRAWN` | ~15 行 |
| 禁止回血 | 无拦截点 | `Player.heal` 前置钩子 | ~10 行 |
| 护甲不清空（类 Calipers） | 需改 `Combat.beginPlayerTurn` | 战斗流程改动 | 中 |

> **本批次建议只动两个**：`Player.reduceMaxEnergy(int)` 与 `BattleInfo` 群体扩展。其余留作后续。
>
> **执行结果**：这两个都动了，且都已完成（见第七节状态表）。「每回合 +1 能量」确认**本期不做** ——
> 它需要 `gainBonusEnergy` 这种"允许破上限"的新语义，改动面比看上去大。
> 其余四行（金币代价 / 抽牌触发点 / 禁止回血 / 护甲不清空）**原样留作后续**。

### 3.3 顺带发现的一个既有问题（与多敌人改造直接相关）

`BattleInfo.addMonsterStacks()` 与 `dealDirectDamageToMonster()` 在多怪编队下**只作用于当前锁定目标**。也就是说：
**心之石（每回合给怪物 1 层易伤）、麻醉剂（开战 2 层虚弱）、毒华（3 张技能牌 2 层中毒）在双怪遭遇里只打一只。** 单怪路径下无感，多怪路径下这三个遗物会明显弱于描述。
这不是本批次引入的，但既然要动 `BattleInfo`，**建议一并决定语义**（推荐：状态叠给全体，穿透伤害仍打锁定目标 —— 前者是"开战布局"，后者是"集火"）。

> **处置结果**：按推荐语义落地了 —— 新增 `BattleInfo.addAllMonsterStacks(StatusEffect, int)`
> （`BattleInfo` 给 `default` 实现，老实现自动降级为单目标；`BattleState` 实现为遍历存活怪）。
> 「心之石 / 麻醉剂 / 毒华」改为走群体版本，**这三个遗物在多怪场合的行为已被修正**；
> 穿透伤害仍打锁定目标，未改。冒烟实测 `venom_heart` 在双怪编队下**两只各中 5 层毒**。

---

## 四、Boss 遗物池设计

### 4.1 池子规模怎么定（12 件）

StS：3 章 × 3 选 1 = 9 次抽取，池子约 20 件 → 每件在一局中露面的概率 ≈ 45%。
本项目：按 2 章算 = 6 次抽取。

| 池子规模 | 每件出现概率 | 手感 |
| --- | --- | --- |
| 6 件 | ≈ 100% | 每局都看到同样 6 件，等于没有池子 |
| **12 件** | **≈ 50%** | **与 StS 手感接近** ✅ |
| 20 件 | ≈ 30% | 一局见不全，构筑方向反而模糊 |

**结论：池子定 12 件**（现有 6 + 新增 6）。

### 4.2 十二件一览（确保任意两件不重叠）

| # | ID | 名称 | 状态 | 正面 | 代价 | 代价货币 | 打法定位 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dark_pact` | 黑暗契约 | 现有 | 能量上限 +1 | 最大生命 −12 | 生命上限 | 资源型 |
| 2 | `crimson_crown` | 猩红王冠 | 现有 | 伤害 +50% | 每战开始失去 20% 当前生命 | 每战生命 | 爆发型 |
| 3 | `leviathan_heart` | 利维坦之心 | 现有 | 最大生命 +40 且回满 | 每回合结束 −2 生命 | 每回合生命 | 血池型 |
| 4 | `aegis_of_ruin` | 废墟之盾 | 现有 | 每回合开始 +6 甲 | 伤害 −20% | 输出 | 防御型 |
| 5 | `soulbound_ledger` | 缚魂账簿 | 现有 | 每战胜利最大生命 +8 | 每战开始最大生命 −2 | 开场血上限 | 成长型 |
| 6 | `doomsday_clock` | 末日之钟 | 现有 | 每第 3 回合伤害 ×2.5 | 其余回合 ×0.85 | 平均输出 | 节奏型 |
| 7 | `iron_edict` | 铁律 | **新增** | 技能牌费用 −1 | 攻击牌费用 +1（最低 0） | 费用结构 | 费用重构型 |
| 8 | `twin_curse` | 双刃 | **新增** | 所有牌费用 −1（最低 0） | 受到的伤害 +25% | 承伤 | 低费连打型 |
| 9 | `predators_crest` | 猎首徽记 | **新增** | 击杀怪物获得 2 层力量 | 每战开始失去 10 点生命 | 开场生命（定额） | 击杀滚雪球型 |
| 10 | `venom_heart` | 毒心 | **新增** | 开战为全体怪物叠 5 层中毒 | 造成的伤害 −10% | 输出 | 毒流型 |
| 11 | `ascetic_vow` | 苦修誓约 | **新增** | 伤害 +80%，每回合 +3 甲 | 能量上限 −1 | 能量 | 苦修型 |
| 12 | `bloodlust_sigil` | 嗜血纹章 | **新增** | 每战开始获得 4 层力量 | 每战开始获得 4 层易伤 | 状态（易伤） | 赌博型 |

> 表中 2/3/5/6 的数值是在第六节的校准结论上**已上修**的值。

### 4.3 新增 6 件的实现说明

**7. `iron_edict` 铁律** —— 触发点：**无**（纯费用函数）
- `modifyCost`：`ATTACK` → `cost + 1`；`SKILL` → `max(0, cost - 1)`；其他类型不变
- **实现要点（已与初稿分道）**：初稿是「能量上限 +1 / 每回合最多 5 张牌」，靠 `cost + 99` 软禁第 6 张牌。
  落地时改掉了 —— `SimpleRelic` 新增可选的 `CostModifier` 函数式接口 + 7 参构造，
  直接做 `cost ± 1`。理由是 `cost + 99` 是一条一眼读不懂的隐晦技巧，而 `CostModifier` 是公开接口。
- **不带触发点 ⇒ 不占用触发点索引**，也不持有跨回合状态，因此继续用 `SimpleRelic` 即可，
  不必像 `EchoChamberRelic` 那样另写独立类。
- 实测：打击（攻1）→ 2、防御（技1）→ 0、痛击（攻2）→ 3。
- **与 #8 的分工**：本件是**定向**重构（技能变便宜、攻击变贵，逼向技能流）；
  #8 是**无差别**减费但承伤涨价。两者放一起会互相拉扯，正好构成取舍。

**8. `twin_curse` 双刃** —— 触发点：`modifyCost` + `DAMAGE_TAKEN`
- `modifyCost`：`cost - 1`（下限由 `RelicService` 保证为 0）
- `DAMAGE_TAKEN`：`multiplyValue(1.25)`
- **结算顺序要写进描述**：`DAMAGE_TAKEN` 的输入是**易伤结算前的原始伤害**，所以实际承伤 = 原值 × 1.25 ×（易伤 ? 1.5 : 1）＝ 最高 1.875 倍。**穿 25% 与易伤是叠乘**，不写清楚玩家会以为只是 +25%。

**9. `predators_crest` 猎首徽记** —— 触发点：`ENEMY_KILLED` + `BATTLE_START`
- `ENEMY_KILLED`：`player.addStacks(STRENGTH, 2)` ｜ `BATTLE_START`：`player.setHealth(max(1, hp - 10))`
- **与 #2 的区别是"定额 vs 比例"**：血多时定额便宜、血少时定额要命；比例恰恰相反。同一维度下给了两种风险口味。
- 力量在 `resetForBattle()` 会被清空，所以成长是**单场内**的，不会跨战斗滚雪球 —— 这是刻意的，避免和 #5 抢生态位。

**10. `venom_heart` 毒心** —— 触发点：`BATTLE_START` + `DAMAGE_DEALT`
- `BATTLE_START`：给**全体**怪物 5 层中毒 → **依赖 3.2 的 `BattleInfo` 群体扩展**
- `DAMAGE_DEALT`：`multiplyValue(0.9)`
- **为什么代价是"伤害"而不是别的**：中毒走的是 `takeDamage`，**不吃 `DAMAGE_DEALT`**。所以「伤害 −10%」几乎不伤毒的输出，只惩罚"顺手平砍"—— 这就把构筑方向明确推向"毒是主输出"。
- **降级方案**：若不做 `BattleInfo` 扩展，则改为"给当前目标 5 层中毒"，并把它**从 Boss 池降为稀有**（多怪场合会明显偏弱）。

**11. `ascetic_vow` 苦修誓约** —— 触发点：`OBTAIN` + `TURN_START` + `DAMAGE_DEALT`
- `OBTAIN`：`player.reduceMaxEnergy(1)` ｜ `TURN_START`：`addArmor(3)` ｜ `DAMAGE_DEALT`：`multiplyValue(1.8)`
- **唯一一件"削减能量"的遗物**：3 能量变 2 能量 ≈ 每回合少打一张牌，所以 +80% 伤害不是白给。
- **硬依赖**：必须新增 `Player.reduceMaxEnergy(int)`，现有 `addMaxEnergy` 拒绝非正数。实现里要同步 `energy = Math.min(energy, maxEnergy)`，否则会出现"当前能量 3 / 上限 2"的非法态。

**12. `bloodlust_sigil` 嗜血纹章** —— 触发点：`BATTLE_START`
- `player.addStacks(STRENGTH, 4)` + `player.addStacks(VULNERABLE, 4)`
- **实现最简单，但时机必须对**：`BATTLE_START` 在 `resetForBattle()` 之后触发（`Combat.java:483 → 508`），所以这两层状态不会被清掉。
- 易伤每回合末 −1，因此 4 层 = **前 4 回合承伤 +50%**；力量是整场。**前 4 回合就是这套的危险窗口**，逼玩家开局就得处理。这是全池子里波动最大的一件，需要重点实测。

---

## 五、产出路径设计（真正的关键路径）

### 5.1 三个断点

```
断点①  GameController.finishBattle()  BOSS 分支直接 return，不发奖       :309-319
断点②  relicRaritiesFor()              不含 BOSS，Boss 遗物永远进不了池    :344-349
断点③  BattleReward                   只有单个 relic，装不下三选一        BattleReward:18
```

### 5.2 改动清单（按模块）

**A. 数据模型（`game/reward`）**
- `BattleReward`：`relic` 之外增加 `List<Relic> relicChoices`（普通掉落 0–1 件、Boss 3 件），并加 `claimRelic(String relicId)` 与 `hasRelicChoices()`。保留 `hasRelic()` 以兼容现有调用。

**B. 流程（`flow`）**

```java
// GameController.finishBattle()
MapNode node = requireCurrentNode();
if (node.type() == MapNodeType.BOSS) {
    currentCombat = null;

    // 只有还有下一章时才发 Boss 遗物（单章模式下行为与现在完全一致）
    if (runState.hasNextAct()) {
        List<Relic> choices = RelicLibrary.bossChoices(
                rewardSeed(node), runState.getPlayer(), 3);
        currentReward = RewardService.forBossRelicChoice(runState, choices, relicService);
        phase = GamePhase.REWARD;
        return;                 // ← 先发奖，玩家选完再推进章节
    }

    getMapService().completeCurrentNode();
    runState.advanceAct();
    phase = runState.hasNextAct() ? GamePhase.MAP : GamePhase.VICTORY;
    return;
}
```

- **⚠️ 最大的坑：顺序必须改。** 现在是"`completeCurrentNode()` → `advanceAct()`"，改后必须"**玩家选完遗物 → 才 `completeCurrentNode()` + `advanceAct()`**"。否则地图已经推到下一章，奖励阶段会挂在一个已完成的节点上。
- 因此 `finishReward()` 需要分支：Boss 遗物奖励结算完后补 `completeCurrentNode()` + `advanceAct()`。
- `rewardSeed(node)` 对 Boss 节点同样成立，**同一局同一 Boss 的候选可复现**，这点不用改。

**C. HTTP（`http`）**
- `GameStateJson.rewardJson()`：先补上 **`relicChoices`** —— 注意现在连普通遗物都没有下发，这是个独立的小缺口。
- 新增 `POST /api/v1/reward/select-relic`（或扩展 `/reward/select` 的 body 加 `relicId`）。
- Boss 奖励不发金币（对齐 StS：Boss 宝箱只给遗物）。

**D. UI**
- 奖励界面渲染 3 张遗物卡：名称 / 描述 / 稀有度 / **代价高亮**（把代价做成视觉上更醒目的部分，才能让"三选一"真的变成取舍而不是看谁数值高）。
- 属 **郑义仁** 的模块，需要跨模块协调。

### 5.3 章节数

- 三处默认 `DEFAULT_ACT_COUNT = 1`（`FlowApp:66`、`GameServer:51`、`GameFlowDebugMain:40`）。
- **建议：把默认改成 2**，并用 `hasNextAct()` 作为发放闸门。这样单章模式行为零变化，两章模式下 Boss 遗物才有实际作用窗口。

---

## 六、数值校准：BOSS 必须严格强于 RARE

### 6.1 倒挂实证

| 收益维度 | 稀有版 | Boss 版 | 判定 |
| --- | --- | --- | --- |
| 能量上限 +1 | `greedy_cup`：最大生命 **−8** | `dark_pact`：最大生命 **−12** | ❌ Boss 更差 |
| 击杀/战斗成长 | `soul_harvest`：击杀最大生命 **+3，无代价** | `soulbound_ledger`：每战净 **+3**，且开战 −2 | ❌ Boss 更差 |

**根因**：StS 里"能量遗物"**全部属于 Boss 池**，普通或稀有掉落拿不到 +1 能量。本项目把 `greedy_cup` 放在稀有，等于让稀有遗物跨级做了 Boss 的事。

**建议（二选一，需 Joy 拍板）**
- **方案 A（推荐）**：把 `greedy_cup` 并入 Boss 池，`dark_pact` 改为另一个收益维度（避免重复）。
- **方案 B（改动更小）**：`greedy_cup` 保持稀有，但代价加重到「最大生命 −20」，明确弱于 Boss 版。

### 6.2 伤害乘区期望值（可算的部分）

Boss 遗物要么给稳定收益、要么给"高波动"。**波动型的期望值必须仍为正、且有明显峰值**，否则玩家不会选。

| 遗物 | 乘区构成 | 平均倍率 | 判定 |
| --- | --- | --- | --- |
| `doomsday_clock`（现状） | ×2 / ×0.6 / ×0.6 | (2+0.6+0.6)/3 = **1.067** | ❌ 只有 +6.7%，配不上 Boss |
| `doomsday_clock`（建议） | ×2.5 / ×0.85 / ×0.85 | (2.5+0.85+0.85)/3 = **1.40** | ✅ +40%，峰值 2.5× |
| `aegis_of_ruin` | 固定 ×0.8 + 每回合 6 甲 | — | 6 甲/回合 ≈ 一张防御牌，**偏强，可保留** |
| `crimson_crown`（建议 +50%） | 固定 ×1.5 | 1.5 | 与稀有 `glass_cannon`（+50%/受+50%）同档，但代价是**每战掉 20% 血**而非永久的承伤增加 —— 更契合 Boss |

### 6.3 上修清单（已体现在 4.2 表中）

| ID | 现状 | 建议 | 理由 |
| --- | --- | --- | --- |
| `crimson_crown` | 伤害 +35% | **+50%** | 不能弱于同类的稀有 `glass_cannon` |
| `leviathan_heart` | 最大生命 +30 | **+40** | 50 血玩家 +30 只有 +60%，扣掉每回合 −2 后净收益太低 |
| `soulbound_ledger` | 每战 +5（−2） | **每战 +8（−2）** | 必须反超稀有的 `soul_harvest` |
| `doomsday_clock` | ×2 / ×0.6 | **×2.5 / ×0.85** | 期望从 +6.7% 提到 +40% |
| `dark_pact` | — | 见 6.1 | 视 `greedy_cup` 的归属再定 |

---

## 七、实现清单与工作量分级

| 批次 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- |
| **P0** | 接产出路径（数据模型 + 流程 + HTTP） | `BattleReward` / `RewardService` / `GameController` / `GameStateJson` / `GameServer` | ✅ 已完成 |
| **P0** | 上修 6 件现有 Boss 遗物数值 | `RelicLibrary` | ✅ 已完成 |
| **P1** | 新增 6 件遗物（原计划 5 件，实际 6 件全上） | `RelicLibrary` | ✅ 已完成 |
| **P1** | `SimpleRelic` 增加可选 `CostModifier`（`iron_edict` / `twin_curse` 用） | `SimpleRelic` | ✅ 已完成（初稿未预见） |
| **P1** | 奖励界面渲染遗物三选一 + 代价高亮 | UI（郑义仁模块） | ⬜ **未做** |
| **P2** | `Player.reduceMaxEnergy(int)` → 解锁 `ascetic_vow` | `Player` | ✅ 已完成 |
| **P2** | `BattleInfo` 群体扩展 → 解锁 `venom_heart` 完整版，并修正心之石 / 麻醉剂 / 毒华的多怪语义 | `BattleInfo` / `BattleState` | ✅ 已完成 |
| **P2** | 默认章节数改 2 | `FlowApp` / `GameServer` / `GameFlowDebugMain` | ✅ 已完成 |
| **P3** | 「每回合 +1 能量」类遗物（需 `gainBonusEnergy`） | `Player` + `RelicLibrary` | ⬜ 本期不做 |

---

## 八、验证方案与执行结果

设计时列的 7 条验证项，实际按「全量单测 + 专项冒烟 + 既有路径回归」三条腿落地。
下面每节都写清**实际跑出来的数字**。

### 8.1 全量单测

`GameControllerTest` 新增 2 个用例，其余用例保持原样：

| 用例 | 断言 |
| --- | --- |
| `bossVictoryShouldOfferThreeBossRelicsBeforeAdvancingAct` | 跑到第一个 Boss 胜利 → `phase == REWARD`、`relicChoices.size() == 3` 且全为 BOSS、无重复；选定后玩家持有该遗物、`getCurrentAct() == 2`、`phase == MAP` |
| `singleActRunShouldNotOfferBossRelicAndShouldFinishDirectly` | `totalActs=1` 时 Boss 胜利直接 `VICTORY`，玩家遗物里不出现 BOSS 稀有度 |

**结果：158 / 158 通过，0 失败**（改动前 156 个用例，本次 +2）。

> 踩坑记录：第一版用例用「固定 30 次迭代」推进地图，结果在走到**商店节点**时计数器耗尽而退出，
> 报错是 `expected: <REWARD> but was: <SHOP>` —— 看起来像流程 bug，其实是测试自己写窄了。
> 地图每个节点要消耗 3 次迭代（MAP → 战斗/非战斗 → REWARD），固定上限必然不够。
> 现已改为**由「是否已停在 Boss 三选一」驱动**，不再按节点数估算。

### 8.2 Boss 遗物池专项冒烟（`BossRelicSmokeTest`，68 项断言全过）

| 组 | 覆盖内容 | 关键实测值 |
| --- | --- | --- |
| 1 | 池构成与稀有度曲线 | `COMMON=13, UNCOMMON=9, RARE=8, BOSS=12`；BOSS(12) ≥ RARE(8)，**倒挂已消除** |
| 2 | 三选一数量 / 去重 / 全 BOSS / 可复现 | 200 个种子全部恰好 3 件、无重复、覆盖整池 12 件；同种子结果完全一致 |
| 3 | 已持有排除 | 已持有件在 100 个种子中不再出现；全池持有时返回空列表 |
| 4 | 费用修正 | 铁律：攻1→2、技1→0、攻2→3；双刃：1→0（下限）、2→1；双刃承伤 10→13 |
| 5 | 苦修誓约 | 能量上限 3→2、当前能量被夹到 2；增伤 10→18；开场回合护甲 +3 |
| 6 | **开战类遗物在多怪编队下打到全体** | 毒心：怪 0 与怪 1 **各 5 层中毒**；嗜血纹章：力量 4 / 易伤 4；猎首徽记：490 血、击杀 +2 力量 |
| 7 | Boss 奖励领取路径 | 不发金币、不发卡牌、无普通遗物；命中/未命中查找；选定后仅发放选中那件；非法选择被拒且不结算；`skipCard` / `claimCard` 均被拒 |
| 8 | `BattleReward` 校验 | 互斥、>3 件、重复 id 三种非法构造均抛 `IllegalArgumentException` |
| 9 | 章节推进语义 | 2 章：有下一章 → `advanceAct` → 第 2 章后无下一章；1 章：直接结束 |
| 10 | 全池逐件压力 | 12 件各获一次 + 遍历 9 个触发点 + 结束回合，全部无异常 |
| 11 | 全池同场 | 12 件同时持有 + 遍历全部触发点，无异常、无递归超限 |

### 8.3 既有路径回归（`MultiTargetSmokeTest`，65 项断言全过）

改动碰了 `BattleState` 与 `BattleInfo`（为群体状态扩展），因此**必须**复跑 #88 的多敌人冒烟：
编队发现、目标选择、击杀后自动切换、剩余伤害转向存活者、每只怪状态独立、木桩路径不回归、
流程层按种子抽编队、HTTP 指定编队、形态钩子 `onHpDepleted` —— **65 / 65 全过，零回归**。

### 8.4 尚未覆盖

- **UI 三选一渲染**（郑义仁模块）：后端 `bossRelicChoice` / `relicChoices` 已下发，界面尚未接。
- **实战数值校验**（风险 R5）：以上都是断言级验证，不是平衡性实测。

---

## 九、风险与待定决策

### 9.1 决策已定（执行时按「最可行方案」拍板）

| # | 问题 | 定案 | 理由 |
| --- | --- | --- | --- |
| 1 | `greedy_cup` 归属 | **保持 RARE，但代价加重到 −15 最大生命** | 不把 RARE 混进 BOSS 池；代价数值压在 `dark_pact`（−12）之下，保住「BOSS 更狠」的强弱秩序 |
| 2 | `BattleInfo` 群体语义 | **状态类效果叠给全体**（新增 `addAllMonsterStacks`），穿透伤害仍打锁定目标 | 顺带修掉「心之石 / 麻醉剂 / 毒华」在多怪场合只命中一只的既有问题 |
| 3 | 默认章节数 | **改为 2**（`FlowApp` / `GameServer` / `GameFlowDebugMain` 三处） | 单章下 Boss 遗物发了没有任何后续步骤能消耗它，等于永远用不上 |
| 4 | `iron_edict` 的「软禁」手法 | **不用 `cost + 99`**，改为在 `SimpleRelic` 里新增可选 `CostModifier`，直接做 `cost ± 1` | 零流程改动的隐晦技巧换成一条一眼能读懂的公开接口，`modifyCost` 的下限仍由 `RelicService` 统一压到 0 |

> 决策 4 的代价是 `SimpleRelic` 从 6 参构造扩到 7 参；已保留 6 参构造转发（传 `null`），
> 既有 36 件遗物**一行都不用改**。

### 9.2 已识别的风险与实际处置

- **R1｜奖励阶段与地图推进的顺序** —— 已按设计规避：`finishBossBattle()` **不发奖时推进章节**，
  推进统一交给 `finishReward()` 在玩家选定后执行；并有单测钉住「选之前 `getCurrentAct() == 1`」。
- **R2｜`venom_heart` 与多怪遭遇的耦合** —— 已由决策 2 解决，冒烟实测双怪**各中 5 层毒**。
- **R3｜`bloodlust_sigil` 波动过大** —— 未处置，仍是**待实测**项：4 层易伤 = 前 4 回合承伤 +50%，
  对 50 血玩家可能变成「选了就死」。
- **R4｜`ascetic_vow` 的能量上限削减** —— 已由 `reduceMaxEnergy` 同步夹紧当前能量，
  冒烟断言「上限 3→2 且当前能量 ≤ 2」「开场能量 = 新上限 = 2」，不会再产生非法状态。
- **R5｜数值未经实战** —— 未处置，仍是**待实测**项：6.2 的期望值基于「每 3 回合一个循环」的静态假设。

---

## 十、建议的执行顺序

1. **先接管线（P0）** —— 这是唯一能让"Boss 遗物"从死代码变成功能的一步，且不依赖任何新 API。
2. **同时上修 6 件现有数值（P0）** —— 让池子先达到"能用"的强度水平。
3. **再做 UI（P1）** —— 没有界面，玩家看不到三选一，管线接了也验证不了。
4. **最后补新遗物与 API 扩展（P1/P2）** —— 新遗物是"锦上添花"，前面三步才是"从 0 到 1"。

> 一句话：**现在的瓶颈不是遗物不够，而是这 6 件从来没有被发出去过。**

---

## 十一、交付状态与差集

### 11.1 工作区相对基准的差集

本次改动的基准不是 `dev`，而是**已推送的 `feature/#88-多敌人目标选择`（`5a00bb04`）** ——
两者叠在同一个工作副本上（#88 的提交是借临时索引造出来的，真实 `.git/index` 仍停在 `dev`，
所以 `git status` 会把 #88 新增的文件显示成未跟踪，这是**索引陈旧**，不是文件丢失）。

用临时索引算出的**真实差集只有 14 项**：

- 新增 1 份：`docs/Boss遗物池设计案.md`
- 修改 13 个：`GameFlowDebugMain` / `GameController` / `BattleState` / `BattleInfo` / `Player` /
  `RelicLibrary` / `SimpleRelic` / `BattleReward` / `RewardService` / `GameServer` /
  `GameStateJson` / `FlowApp` / `GameControllerTest`
- 删除 0 个

合计 **14 files changed, +952 / −40**。

### 11.2 基准漂移提醒

远程状态（`ls-remote` 核对）：

| ref | 提交 | 说明 |
| --- | --- | --- |
| `feature/#88-多敌人目标选择` | `5a00bb04` | 完好，未被动过 |
| `dev` | `7907fd7c` | **已前进**（当初推 #88 时是 `1d2c8da`） |
| `master` | `1a833dce` | 未变 |

也就是说 #88 的父提交已经不是 `dev` 顶点了。合回 `dev` 时**不再是快进**，而是一次普通合并；
若 `dev` 上碰过 `Combat` / `MonsterAi` / 遗物相关文件，需要先处理冲突。

### 11.3 本批次未做的事

- 未提交、未推送、未改动任何 ref（工作副本保持原样，等你确认）。
- 未动 UI（三选一的界面渲染归郑义仁模块）。
- 独立补丁已生成：`boss-relic-pool.patch`（含新增文档，`git apply` 可直接用）。

## 十二、落地记录：`feature/#95` 分支（叠在 dev 最新顶点上）

`dev` 在开发期间被推了三次（`1d2c8da` → `7907fd7c` → `9b2905b` → `5d5a6be5`）。
其中的 `9b2905b`（PR #94 / `feature/#92-change`）带进一整套 `game/blessing/*`
开局房间子系统，并新增了 `GamePhase.BLESSING`；`5d5a6be5`（PR #97 / `feature/#93`）
重写了地图生成。我改的 13 个文件里有 **8 个与 dev 重叠**，所以**不能直接推工作副本**
（那会回退掉 dev 的改动），必须把改动重落到 dev 顶点上。

> 落地时踩到的现实问题：**第一次准备推送时 `dev` 又动了**（`9b2905b` → `5d5a6be5`）。
> 推送脚本里带了一道「推送前重新 `ls-remote`，顶点变了就中止」的闸门，因此那次没有推出去，
> 而是按新顶点把整套重落重放了一遍。分支的父提交必须**正好等于**落地那一刻的 dev 顶点，
> 否则合回 dev 就不是快进。

### 12.1 8 个冲突文件的裁决

| 文件 | 冲突性质 | 裁决 |
| --- | --- | --- |
| `GameFlowDebugMain` | 只有 `DEFAULT_ACT_COUNT` 同一行的注释之争 | **取 dev**（dev 已把 1 改成 2，功能目标已达成）→ 与 dev 完全一致 |
| `FlowApp` | 同上 | **取 dev** → 与 dev 完全一致 |
| `RewardService` | 类注释 | 取我的（「普通、精英或 Boss 战斗」更准） |
| `GameServer` | `DEFAULT_ACT_COUNT` 注释 | **取 dev**；只保留我的 `POST /api/v1/reward/select-relic` 端点 |
| `GameStateJson` | dev 也下发了 `relic`（内联 id / name / description / icon） | **取我的**：同一份 `relicJson()` 是超集（多 `rarity`），并追加 `bossRelicChoice` / `relicChoices` |
| `RelicLibrary` | dev 也新增了 `TOWER_KEY` 常量与注册 | 常量去重（留带 javadoc 的那份）；注册块两边一致只留一份；我的 6 件新增 / 6 件上修 / `BOSS_POOL_EXCLUDED` 全部保留 |
| `GameController` | **真设计冲突**：dev 给 Boss 发「150 金 + 稀有牌 + 高塔之匙」，我原来让 Boss 跳过奖励只发三选一 | 按 12.2 的「串联」裁决 |
| `GameControllerTest` | import 并集 + 注释 | 取并集；并按「串联」调整两处断言（见 12.5） |

### 12.2 Boss 奖励的最终形态：串联两段

dev 的 Boss 奖励**原样保留**（`BOSS_GOLD_REWARD = 150` + 稀有牌 + 定点 `TOWER_KEY`），
只在它结算完之后**追加**第二段「Boss 遗物三选一」。三条要点：

1. 第一段结算完**不推进章节**，第二段选定后才 `completeCurrentNode()` + `advanceAct()`。
   否则地图已经翻到第二章、奖励阶段还挂在旧节点上，选完会再推一次 —— 直接跳过一章。
2. 三选一的闸门是 `runState.hasNextAct()`：单章模式下打完 Boss 即通关，退回原有行为，
   默认单章流程与改动前等价。
3. 第二段是「无金币、无卡牌、必须选定一件」；`skipCard()` / `claimCard()` 被
   `ensureCardReward()` 拦住，避免「什么都没发却把奖励标记成已结算」。

### 12.3 「高塔之匙」不进随机池

dev 把 `TOWER_KEY` 注册成 `RelicRarity.BOSS` 的占位遗物（无任何战斗效果）。
它**必须**是 BOSS 稀有度（否则会掉进普通 / 精英掉落池），但**不能**出现在三选一里 ——
那等于给玩家一个「死选项」。因此新增 `BOSS_POOL_EXCLUDED = Set.of(TOWER_KEY)`，
`bossChoices()` 跳过它。于是 `bossIds()` 是 13 件，而随机池仍是 **12 件**。

### 12.4 顺带发现的 dev 问题（都不是本次改动引入的）

| 问题 | 证据 | 处置 |
| --- | --- | --- |
| **dev 编译不过**：`GameServer.handleBlessing` / `handleChooseBlessing` 调用 `requireRun(ex)`，但 dev 全树没有任何地方定义它 | `javac`：找不到符号 `requireRun(HttpExchange)`（`GameServer.java:311`、`:341`）；在 `9b2905b` 与 `5d5a6be5` 上均如此 | 本分支补上语义一致的最小实现（`return requireController(ex) != null;`），合并进 dev 后该问题随之消失 |
| ~~一条既有单测失败：`GameControllerTest.eventChoiceShouldResolveEventAndCompleteMapNode`~~ | 在 `9b2905b` 上：把 dev 顶点单独抽出、只为编译补上 `requireRun` 后运行 → `169 found / 1 failed`（`getAvailableNodes().getFirst()` 越界） | **已被上游修掉**：dev 前进到 `5d5a6be5`（PR #97 重写 `MapGenerator`）后，同一用例在合并结果上通过，全量 173/173 绿 |

### 12.5 验证（全部在「dev `5d5a6be5` + 本次改动」的合并结果上实跑）

- 主源码 **134 文件编译通过（164 class）**，测试源码 **29 文件（33 class）**，0 错误。
- 全量单测 **173 found / 173 passed / 0 failed**（我新增的 2 个 Boss 用例在内）。
- Boss 遗物池专项冒烟 **72 / 72**（原 68 项，新增 4 项钉住「高塔之匙不进三选一」）。
- `MultiTargetSmokeTest` **65 / 65** —— 本次碰了 `BattleState` / `BattleInfo`，但 #88 路径零回归。
- 稀有度分布实测 `COMMON=13 / UNCOMMON=9 / RARE=8 / BOSS=13`（含高塔之匙），倒挂已消除。

因为 Boss 奖励变成两段，`GameControllerTest` 里两处断言随之调整（都在用例里写了原因）：
`completeOneNode` 改为把两段奖励都走完；dev 的
`bossVictoryShouldOfferTowerKeyRareCardsAndGoldThenAdvanceAct` 在领完第一段后再走一次三选一，
并断言「选遗物之前章节不推进」。

### 12.6 分支形态

`feature/#95-...` 的父提交就是落地时的 `dev` 顶点 `5d5a6be5`，
所以**合回 dev 是快进、零冲突** —— 这正是这次重落的全部意义。
分支相对 dev 的差集：**12 项**（1 份本文档 + 11 个文件，`+1172 / −48`，0 删除）。
`GameFlowDebugMain` 与 `FlowApp` 因为裁决取了 dev，已不在差集里。
