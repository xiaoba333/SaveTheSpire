# SaveTheSpire 前后端 HTTP 接口协议（v1.0 定稿）

> 用途：约定 Unity 客户端（UI）与 Java 后端之间的通信契约。
> 架构：后端权威 —— 战斗逻辑全部在 Java 后端计算，Unity 只负责「发操作 + 渲染状态」。
> 状态：**v1.0 定稿**（已逐条确认）。

---

## 1. 总则

| 项 | 约定 |
|----|------|
| 通信方式 | HTTP，请求/响应，暂不引入 WebSocket |
| 数据格式 | JSON，`Content-Type: application/json; charset=utf-8` |
| 字段命名 | **camelCase**（小驼峰），Jackson 默认即可 |
| 版本前缀 | 所有路径以 `/api/v1` 开头 |
| 基础地址 | 开发期 `http://localhost:8080`，**端口做成可配置**，不写死（Unity 侧在配置里读） |

### 1.1 状态码约定

| HTTP 状态码 | 含义 |
|-------------|------|
| `200` | 成功 |
| `400` | 业务错误（能量不足、非玩家回合、卡牌不存在等） |
| `404` | `battleId` 不存在 |
| `500` | 服务器内部错误 |

### 1.2 错误响应格式

```json
{ "code": "NOT_ENOUGH_ENERGY", "message": "能量不足" }
```

| code | 含义 |
|------|------|
| `NOT_ENOUGH_ENERGY` | 能量不足，无法打出该牌 |
| `NOT_PLAYER_TURN` | 当前不是玩家回合 |
| `INVALID_CARD` | 手牌中不存在该 cardId |
| `CARD_NOT_PLAYABLE` | 该牌不可打出 |
| `BATTLE_NOT_FOUND` | 战斗不存在或已过期 |
| `BATTLE_FINISHED` | 战斗已结束，无法操作 |

---

## 2. 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/battles` | 开始一场新战斗（可选指定 `encounterId` 编队） |
| `POST` | `/api/v1/battles/{battleId}/target` | **切换攻击目标**（多敌人战斗用） |
| `POST` | `/api/v1/battles/{battleId}/play` | 打出手牌中某张牌（按 cardId，可选带目标） |
| `POST` | `/api/v1/battles/{battleId}/end-turn` | 玩家结束回合（后端同步执行怪物行动） |
| `GET`  | `/api/v1/battles/{battleId}` | 查询当前战斗状态（重连 / 刷新用） |
| `GET`  | `/api/v1/battles/{battleId}/piles` | 查看抽牌堆 / 弃牌堆的牌面内容（点开牌堆时按需拉取） |

> 每次操作接口都返回完整 `BattleState`，Unity 拿到响应直接整屏刷新。

---

## 3. 数据模型

### 3.1 BattleState（所有接口的响应主体）

```json
{
  "battleId": "550e8400-e29b-41d4-a716-446655440000",
  "turnNumber": 1,
  "phase": "PLAYER_TURN",
  "player": { "..." },
  "enemies": [ { "..." } ],
  "hand": [ { "..." } ],
  "piles": { "draw": 5, "discard": 0, "exhaust": 0 },
  "result": null,
  "newLogs": [ "战斗开始。玩家 HP 50，怪物 HP 30。" ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `battleId` | string | 战斗唯一标识（UUID），后端开局生成 |
| `turnNumber` | int | 当前第几回合（从 1 开始） |
| `phase` | string | `PLAYER_TURN` / `VICTORY` / `DEFEAT` |
| `player` | UnitState | 玩家状态 |
| `enemies` | UnitState[] | 敌人数组。**支持多敌人编队**（两条蛆、探险者二人组等），单怪战斗为单元素数组。`index` 即切换目标用的下标 |
| `hand` | CardInstance[] | 当前手牌，按显示顺序 |
| `piles` | object | 各牌堆数量：`draw`/`discard`/`exhaust` |
| `result` | string? | `"VICTORY"` / `"DEFEAT"` / `null` |
| `newLogs` | string[] | 本次操作产生的增量日志 |

### 3.2 UnitState（战斗单位）

```json
{ "hp": 50, "maxHp": 50, "armor": 5, "energy": 3, "maxEnergy": 3, "intent": null }
```

敌人额外带多敌人相关字段：

```json
{
  "index": 1, "id": "explorer_female", "name": "探险者女",
  "hp": 20, "maxHp": 20, "armor": 10,
  "strength": 0, "alive": true, "targeted": false,
  "intent": { "type": "ATTACK", "value": 0 },
  "buffs": [ { "..." } ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `hp` / `maxHp` | int | 当前 / 上限生命 |
| `armor` | int | 当前护甲 |
| `energy` / `maxEnergy` | int? | 仅玩家有，敌人返回 `0` |
| `intent` | Intent? | 仅敌人有，玩家返回 `null`。`{type, value}` 见下表 |
| `index` | int? | **仅敌人**。敌人下标，传给 `/target` 或 `play` 的 `targetIndex` |
| `id` | string? | **仅敌人**。怪物定义 id，用于取立绘；也可作为 `targetId` |
| `name` | string? | **仅敌人**。显示名，同名敌人靠 `index` 区分 |
| `strength` | int? | **仅敌人**。当前力量（含状态加成） |
| `alive` | bool? | **仅敌人**。是否存活；死亡的敌人不能再被选为目标 |
| `targeted` | bool? | **仅敌人**。是否为玩家当前锁定的攻击目标，前端据此高亮 |

#### intent 的两个字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | `ATTACK` / `DEFEND` / `ATTACK_DEBUFF` / `DEFEND_BUFF` / `BUFF` / `DEBUFF` / `HEAL` / `SPECIAL` / `UNKNOWN`。脚本怪 AI 目前把组合意图塌缩成 `ATTACK` / `DEFEND` |
| `value` | int | 叠在意图图标上的数字：攻击是**伤害（含力量）**、防御是格挡量、削弱是层数、回复是回血量。**0 表示没有数字可显示**，前端不画（不是「显示 0」） |

> `value` 2026-09-14 之前恒为 0（脚本怪 AI 的 `IntentSnapshot` 硬编码），所以前端那排意图数字一直不显示。
> 现在由 `MonsterAi.snapshotOf` 从意图自身声明的 `Intent.amount()` 取值，攻击再补上怪物当前力量。

### 3.3 CardInstance（手牌中的一张牌实例）

> ⚠️ 后端需要区分「定义 id」与「实例 id」：`definitionId` 是卡牌定义（`CardLibrary` 里的 `strike`），
> 多张同名牌共享；`id` 是**手牌实例唯一 id**（后端在手牌层分配，建议 UUID），出牌按 `id` 定位。

```json
{
  "id": "3f2c-9a1b-0001",
  "definitionId": "strike",
  "name": "打击",
  "type": "ATTACK",
  "cost": 1,
  "effectiveCost": 1,
  "upgraded": false,
  "upgradable": true,
  "description": "造成 6 点伤害。",
  "exhausts": false,
  "playable": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | **实例唯一 id**（出牌时传给后端定位手牌） |
| `definitionId` | string | 牌定义 id（对应后端 `CardLibrary`） |
| `name` | string | 显示名 |
| `type` | string | `ATTACK`/`SKILL`/`POWER`/`STATUS`/`CURSE` |
| `cost` | int | 能量费用 |
| `effectiveCost` | int | 考虑升级后的实际能量费用 |
| `upgraded` | bool | 当前牌实例是否已升级 |
| `upgradable` | bool | 是否允许升级，可用于判断锻造目标 |
| `description` | string | 效果说明 |
| `exhausts` | bool | 打出后是否进消耗堆 |
| `playable` | bool | 是否可打出 |

### 3.4 Intent（敌人下回合意图）

```json
{ "type": "ATTACK", "value": 10 }
```

`type`：`ATTACK` / `DEFEND`（后续可扩展 `BUFF`/`DEBUFF`）。

---

## 4. 接口详情

### 4.1 开始战斗

```
POST /api/v1/battles
请求体：{}（无参数）
响应 200：BattleState（turnNumber=1，phase=PLAYER_TURN，hand 含 5 张牌，每张带实例 id）
```

联调多敌人时可指定编队，直接开出双怪战斗：

```json
{ "encounterId": "act1_grubs" }
```

可用编队：`act1_grubs`（两条蛆）、`act1_explorers`（探险者二人组）。

### 4.2 出牌

```
POST /api/v1/battles/{battleId}/play
请求体：
{ "cardId": "3f2c-9a1b-0001" }
响应 200：BattleState（出牌后的最新状态）
响应 400：错误对象（能量不足 / 非玩家回合 / 卡牌不存在 / 不可打出 / 战斗已结束 / 目标无效）
```

锻造牌出牌时，额外传入要升级的目标手牌实例 id：

```json
{ "cardId": "forge-instance-id", "targetCardId": "strike-instance-id" }
```

`targetCardId` 仅锻造牌使用；普通牌可以省略。

**多敌人**：可选带 `targetIndex` 或 `targetId`，表示「先把目标切到它，再打出这张牌」
（前端把牌直接拖到某只敌人身上就是这个语义）。单目标卡牌打向锁定目标；
`血雨`、`暮色帷幕` 这类群体卡无视目标，命中全部存活怪。

```json
{ "cardId": "strike-instance-id", "targetIndex": 1 }
{ "cardId": "strike-instance-id", "targetId": "explorer_female" }
```

### 4.3 切换攻击目标

```
POST /api/v1/battles/{battleId}/target
请求体：{ "index": 1 }                      或   { "monsterId": "explorer_female" }
响应 200：BattleState（enemies[].targeted 已更新）
响应 400：INVALID_TARGET（目标不存在或已阵亡）
```

目标阵亡后后端会**自动**把锁定目标切到第一只存活怪，前端不需要自己处理。

### 4.4 结束回合

```
POST /api/v1/battles/{battleId}/end-turn
请求体：{}
响应 200：BattleState（后端同步执行怪物行动后返回）
```

### 4.5 查询状态

```
GET /api/v1/battles/{battleId}
响应 200：BattleState
```

### 4.6 查看牌堆内容

```
GET /api/v1/battles/{battleId}/piles
响应 200：
{
  "draw":    [ CardInstance, ... ],
  "discard": [ CardInstance, ... ]
}
```

`BattleState.piles` 只带三个堆的**数量**——每出一张牌都会拉一次战斗状态，把整堆牌都塞进去是白白的流量。
牌面内容只在玩家点开堆的时候用这个端点单独取一次，元素与手牌同构（见 §4.1 的 card 字段）。

顺序：`draw` 的下一张在数组末尾，`discard` 最近弃入的在数组末尾。

错误：战斗编号不存在或已过期 → `404`，体为 `{"code":"BATTLE_NOT_FOUND","message":"战斗不存在或已过期"}`（与 `/play`、`/end-turn` 同一套）。

---

## 5. 完整流程示例

### ① 开局

```
POST /api/v1/battles  {}
```

```json
{
  "battleId": "550e8400-e29b-41d4-a716-446655440000",
  "turnNumber": 1,
  "phase": "PLAYER_TURN",
  "player": { "hp": 50, "maxHp": 50, "armor": 0, "energy": 3, "maxEnergy": 3, "intent": null },
  "enemies": [
    { "hp": 30, "maxHp": 30, "armor": 0, "energy": null, "maxEnergy": null,
      "intent": { "type": "ATTACK", "value": 10 } }
  ],
  "hand": [
    { "id": "3f2c-9a1b-0001", "definitionId": "strike", "name": "打击", "type": "ATTACK", "cost": 1, "description": "造成 6 点伤害。", "exhausts": false, "playable": true },
    { "id": "3f2c-9a1b-0002", "definitionId": "defend", "name": "防御", "type": "SKILL", "cost": 1, "description": "获得 5 点护甲。", "exhausts": false, "playable": true }
  ],
  "piles": { "draw": 5, "discard": 0, "exhaust": 0 },
  "result": null,
  "newLogs": [ "战斗开始。玩家 HP 50，怪物 HP 30。", "—— 玩家回合 —— 能量 3，抽牌 5 张。" ]
}
```

> 示例 hand 只写 2 张，实际开局返回 5 张。

### ② 打出「打击」

```
POST /api/v1/battles/550e8400.../play  { "cardId": "3f2c-9a1b-0001" }
```

```json
{
  "battleId": "550e8400-e29b-41d4-a716-446655440000",
  "turnNumber": 1,
  "phase": "PLAYER_TURN",
  "player": { "hp": 50, "maxHp": 50, "armor": 0, "energy": 2, "maxEnergy": 3, "intent": null },
  "enemies": [
    { "hp": 24, "maxHp": 30, "armor": 0, "energy": null, "maxEnergy": null,
      "intent": { "type": "ATTACK", "value": 10 } }
  ],
  "hand": [ "剩余 4 张..." ],
  "piles": { "draw": 5, "discard": 1, "exhaust": 0 },
  "result": null,
  "newLogs": [ "玩家打出「打击」，消耗 1 点能量。", "对怪物造成 6 点伤害。" ]
}
```

---

## 6. 定稿结论（逐条确认）

1. **端口与部署**：默认 `http://localhost:8080`，**可配置**，客户端不写死；路径前缀 `/api/v1`。
2. **出牌标识**：**`cardId`**（实例唯一 id）。后端需在手牌层分配实例 id（建议 UUID），与 `definitionId` 分离。
3. **字段命名**：camelCase（小驼峰），Jackson 默认。
4. **敌人结构**：`enemies` 数组，**已支持多敌人编队**（普通节点会抽到两条蛆、探险者二人组；精英与 Boss 仍为单怪）。单怪战斗仍是单元素数组，前端无需分支。切换目标用 `POST /{battleId}/target` 或在 `play` 里带 `targetIndex` / `targetId`。
5. **日志**：`newLogs` 增量。后端需加**日志缓冲区**，每次操作后返回新增日志并清空；单次最多 50 条。
6. **账号/多人**：MVP 无登录；后端生成 `battleId`，Unity 存内存或 PlayerPrefs；当前一台机器一个战斗实例。
7. **战斗过期**：MVP 只存后端内存，重启失效；开发期设置 **30 分钟无操作清理**。
8. **数值**：防御牌 **5 点护甲**（以 `CardLibrary` 代码和卡牌文档为准，旧 README 的 6 是早期版本）。

---

## 7. 非战斗接口（map / reward / shop / event / deck / character）

> 后端已从「独立战斗服务（`BattleServer`）」升级为「统一游戏服务（`GameServer`）」：一个进程承载一整局
> 权威状态（`RunState` + `GameController`），把下面的非战斗端点与第 4 节的 4 个战斗端点接到真实逻辑上。
> 前端把 `GameSettings.UseHttpBackend` 置 `true` 即可走完整流程：菜单 → 地图 → 战斗 → 奖励 → 商店/事件/篝火 → 牌组/角色。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET`  | `/api/v1/characters` | 列出可选角色（不含隐藏角色 god） |
| `POST` | `/api/v1/game/start` | 用选中角色开局（前端选角界面入口，请求体只需 `characterId`） |
| `POST` | `/api/v1/runs` | 同上，另可传 `seed` / `actCount` 复现同一局；可重复调用以重开 |
| `GET`  | `/api/v1/runs` | 查询当前局阶段和角色状态 |
| `GET`  | `/api/v1/map` | 拉取整张地图 |
| `POST` | `/api/v1/map/advance` | 进入节点（任意类型都只「进入」，结算见 7.1） |
| `GET`  | `/api/v1/reward` | 拉取当前战斗奖励 |
| `POST` | `/api/v1/reward/select` | 选择一张奖励卡（cardId=null 表示跳过） |
| `GET`  | `/api/v1/shop` | 拉取商店状态（含可删卡列表与删卡价） |
| `POST` | `/api/v1/shop/buy` | 购买一件商品 |
| `POST` | `/api/v1/shop/remove` | 删除牌组中一张卡（`cardId`=牌组卡实例 id） |
| `POST` | `/api/v1/shop/leave` | 离开商店并结算节点，返回最新地图 |
| `GET`  | `/api/v1/event` | 拉取当前事件与选项 |
| `POST` | `/api/v1/event/choose` | 结算事件选项（choiceId=null 视作 `leave`），返回最新地图 |
| `GET`  | `/api/v1/campfire` | 拉取篝火操作与可锻造卡牌 |
| `POST` | `/api/v1/campfire/act` | 执行篝火操作（`actionId`=rest/smith/leave），返回最新地图 |
| `GET`  | `/api/v1/deck` | 拉取当前牌组 |
| `GET`  | `/api/v1/character` | 拉取角色状态 |

### 7.1 地图推进语义（关键）

`map/advance` 对**任意节点类型都只做「进入」**（后端 `GameController.selectNode`，由它按节点类型切到
战斗 / 事件 / 商店 / 休息阶段）；随后由各自的结算端点离开节点、统一回到地图阶段：

- **BATTLE / ELITE / BOSS**：`advance` 进入即开战，随后前端调 `POST /battles` 取当前战斗；战斗结束走 `reward/*`。
- **EVENT**：`advance` 进入后 `GET /event` 取事件与选项，`POST /event/choose` 结算。
- **SHOP**：`advance` 进入后 `GET /shop`，用 `shop/buy`、`shop/remove` 操作，`shop/leave` 离开。
- **REST（篝火）**：`advance` 进入后 `GET /campfire`，用 `campfire/act`（rest=回复 / smith=锻造升级 / leave=直接离开）结算。

即「**进入**」与「**结算**」分离：进入只切阶段、不动地图进度；结算端点负责推进节点并把地图状态回给前端。

节点 `id` 为**数字字符串**（如 `"0"`），`type` 用大写（`BATTLE/ELITE/EVENT/SHOP/REST/BOSS`），
`state` 为 `LOCKED/SELECTABLE/CURRENT/PASSED`，`column`=层（0 在下、Boss 在最上）、`row`=同层横向位置。

### 7.2 非战斗卡牌 JSON（与 3.3 手牌卡的差异）

非战斗卡牌不带 `effectiveCost/upgraded/upgradable`，但带 `color`（后端未下发时前端默认 `red`）：

```json
{ "id": "quick_slash", "definitionId": "quick_slash", "name": "快斩",
  "type": "ATTACK", "cost": 0, "description": "造成 3 点伤害。",
  "exhausts": false, "playable": true, "rarity": "COMMON", "color": "red" }
```

- 奖励候选卡的 `id` = `definitionId`（前端选卡时回传该 `id`，后端据此匹配）。
- 牌组卡的 `id` = 实例唯一 id（UUID）。
- `rarity`：牌组统一 `BASIC`、奖励候选 `COMMON`、商店商品按商品定义；后端 `Card` 暂无稀有度字段，属占位。

### 7.3 请求 / 响应示例

```jsonc
// GET /api/v1/characters → CharacterList
{ "characters": [
    { "id":"warrior","name":"铁血战士","description":"...","maxHealth":50,"maxEnergy":3,"startingGold":0 },
    { "id":"blood","name":"血祭者","description":"...","maxHealth":30,"maxEnergy":3,"startingGold":0 }
] }

// POST /api/v1/game/start  请求 { "characterId":"warrior" }  → CharacterState
{ "name":"铁血战士","hp":50,"maxHp":50,"gold":0,"relics":[ /* Relic */ ] }

// POST /api/v1/runs  请求 { "characterId":"warrior", "seed":12345, "actCount":1 }
// seed / actCount 可省略（seed 省略则随机、actCount 省略则 1）。隐藏角色可传 characterId=god。
// 响应 RunState：
{ "phase":"MAP", "character": { "name":"铁血战士","hp":50,"maxHp":50,"gold":0,"relics":[] } }

// GET /api/v1/map → MapState
{ "nodes": [ { "id":"0","type":"BATTLE","column":0,"row":0,"state":"SELECTABLE","nextIds":["3"] } ],
  "currentNodeId": "0" }

// POST /api/v1/map/advance  请求 { "nodeId": "0" }  → MapState

// GET /api/v1/reward → RewardState
{ "gold": 20, "cardChoices": [ /* CardInstance */ ] }

// POST /api/v1/reward/select  请求 { "cardId": "quick_slash" }（null=跳过） → RewardState（选完返回空）

// GET /api/v1/shop → ShopState
// items 只含未售出的商品；removableCards 是牌组中可删的卡（实例 id）。
{ "gold": 20, "items": [ { "id":"c_strike","name":"打击","kind":"CARD","price":45,
    "description":"造成 6 点伤害。","rarity":"COMMON","sold":false } ],
  "removableCards": [ /* CardInstance */ ],
  "cardRemovalUsed": false, "cardRemovalPrice": 75 }

// POST /api/v1/shop/buy  请求 { "itemId": "c_strike" }  → ShopState
// POST /api/v1/shop/remove  请求 { "cardId": "<牌组卡实例 id>" }  → ShopState（每家商店限一次）
// POST /api/v1/shop/leave  → MapState

// GET /api/v1/event → EventState
{ "id":"broken_statue","title":"破损的雕像","description":"...",
  "choices":[ { "id":"pray","label":"虔诚祈祷（恢复 5 点生命）","description":"...","disabled":false } ] }

// POST /api/v1/event/choose  请求 { "choiceId": "pray" }（null=离开） → MapState

// GET /api/v1/campfire → CampfireState
{ "actions": [ { "id":"rest","label":"休息","description":"...","available":true,"unavailableReason":"" },
               { "id":"smith","label":"锻造","description":"...","available":true,"unavailableReason":"" } ],
  "upgradeableCards": [ /* CardInstance */ ] }

// POST /api/v1/campfire/act  请求 { "actionId":"rest" } 或 { "actionId":"smith", "cardId":"<牌组卡实例 id>" }
//                             或 { "actionId":"leave" }  → MapState

// GET /api/v1/deck → DeckState
{ "cards": [ /* CardInstance */ ] }

// GET /api/v1/character → CharacterState
{ "name":"血祭者","hp":30,"maxHp":30,"gold":0,"relics":[] }
```

### 7.4 新增错误码

| code | 含义 |
|------|------|
| `NO_ACTIVE_RUN` | 尚未开局（`POST /game/start` 或 `POST /runs`）就调用了其余端点 |
| `INVALID_CHARACTER` | characterId 缺失或不存在 |
| `INVALID_ACT_COUNT` | actCount 小于等于 0 |
| `NO_ACTIVE_BATTLE` | 未通过地图进入战斗就调用了 `POST /battles` |
| `INVALID_TARGET` | 切换目标失败：`index` / `monsterId` 缺失、目标下标越界、或该敌人已阵亡 |
| `INVALID_NODE` | nodeId 非法 / 节点被锁 / 当前阶段不能进入 |
| `INVALID_CARD` | 奖励选卡时 cardId 不在候选中；或 `shop/remove` 缺 cardId |
| `INVALID_ITEM` | `shop/buy` 缺 itemId，或当前不在商店阶段 |
| `INVALID_CHOICE` | 事件选项不存在或当前不可选 |
| `INVALID_ACTION` | 篝火 actionId 未知 / 阶段不符；或 `shop/leave` 阶段不符 |

商店操作失败时直接以状态名作为 code 返回（与 `ShopActionResult` 枚举同名）：

| code | 含义 |
|------|------|
| `ITEM_NOT_FOUND` | 商品不存在 |
| `ITEM_ALREADY_SOLD` | 商品已售出 |
| `CARD_NOT_FOUND` | 牌组中不存在该卡牌 |
| `INSUFFICIENT_GOLD` | 金币不足 |
| `CARD_REMOVAL_ALREADY_USED` | 本商店已删除过卡牌（每家限一次） |
| `SHOP_CLOSED` | 商店已关闭 |

> MVP 已知限制：战斗失败（DEFEAT）后流程进入终局、无重试；Boss 胜利后无胜利界面（地图走完）；
> 篝火「休息」回复最大生命的 30%（生命已满时不可用）；奖励金币为战斗 20 / 精英 35。
