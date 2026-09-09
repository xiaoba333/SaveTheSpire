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
| `POST` | `/api/v1/battles` | 开始一场新战斗 |
| `POST` | `/api/v1/battles/{battleId}/play` | 打出手牌中某张牌（按 cardId） |
| `POST` | `/api/v1/battles/{battleId}/end-turn` | 玩家结束回合（后端同步执行怪物行动） |
| `GET`  | `/api/v1/battles/{battleId}` | 查询当前战斗状态（重连 / 刷新用） |

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
| `enemies` | UnitState[] | 敌人数组。后端当前单怪物，HTTP 层包成**单元素数组** |
| `hand` | CardInstance[] | 当前手牌，按显示顺序 |
| `piles` | object | 各牌堆数量：`draw`/`discard`/`exhaust` |
| `result` | string? | `"VICTORY"` / `"DEFEAT"` / `null` |
| `newLogs` | string[] | 本次操作产生的增量日志 |

### 3.2 UnitState（战斗单位）

```json
{ "hp": 50, "maxHp": 50, "armor": 5, "energy": 3, "maxEnergy": 3, "intent": null }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `hp` / `maxHp` | int | 当前 / 上限生命 |
| `armor` | int | 当前护甲 |
| `energy` / `maxEnergy` | int? | 仅玩家有，敌人返回 `null` |
| `intent` | Intent? | 仅敌人有，玩家返回 `null` |

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

### 4.2 出牌

```
POST /api/v1/battles/{battleId}/play
请求体：
{ "cardId": "3f2c-9a1b-0001" }
响应 200：BattleState（出牌后的最新状态）
响应 400：错误对象（能量不足 / 非玩家回合 / 卡牌不存在 / 不可打出 / 战斗已结束）
```

### 4.3 结束回合

```
POST /api/v1/battles/{battleId}/end-turn
请求体：{}
响应 200：BattleState（后端同步执行怪物行动后返回）
```

### 4.4 查询状态

```
GET /api/v1/battles/{battleId}
响应 200：BattleState
```

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
4. **敌人结构**：`enemies` 数组（当前 1 个，后端 HTTP 层包成单元素数组）。
5. **日志**：`newLogs` 增量。后端需加**日志缓冲区**，每次操作后返回新增日志并清空；单次最多 50 条。
6. **账号/多人**：MVP 无登录；后端生成 `battleId`，Unity 存内存或 PlayerPrefs；当前一台机器一个战斗实例。
7. **战斗过期**：MVP 只存后端内存，重启失效；开发期设置 **30 分钟无操作清理**。
8. **数值**：防御牌 **5 点护甲**（以 `CardLibrary` 代码和卡牌文档为准，旧 README 的 6 是早期版本）。
