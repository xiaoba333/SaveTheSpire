# Roguelike Dungeon

基于 Java 21 + OpenJFX 21 的回合制卡牌游戏 MVP，规则参考《杀戮尖塔》：点手牌打出、结束回合后怪物行动，打到一方血量归零。

当前只做最小可玩版本，界面为简单文字布局，没有额外卡牌和特效。

## 运行环境

- JDK 21
- Maven 3.9+（可用项目自带的 `mvnw`，不必单独安装）

本机若已配置 `JAVA_HOME` 指向 JDK 21，在项目根目录执行：

```powershell
.\mvnw.cmd javafx:run
```

Cursor / VS Code 中也可以用任务 **Run JavaFX Game**，或运行 `com.roguelike.dungeon.Launcher`。

> 不要直接把 `App`（`javafx.application.Application` 子类）当成普通 `main` 启动。请走 `Launcher` 或 `javafx:run`。

## 怎么玩

1. 每回合抽 5 张手牌，点击卡牌即可打出。
2. 打完或放弃剩余手牌后，点「结束回合」。
3. 怪物行动结束后，再回到玩家回合。
4. 怪物 HP ≤ 0 胜利，玩家 HP ≤ 0 失败。

## 规则

| 对象 | 数值 |
| --- | --- |
| 玩家生命 | 50（需求未指定，MVP 暂定） |
| 怪物生命 | 30 |
| 攻击牌 | 对怪物造成 6 点伤害 |
| 防御牌 | 玩家获得 6 点护盾 |
| 怪物攻击 | 对玩家造成 10 点伤害 |
| 怪物防御 | 给自己 +10 护盾 |

- **护盾优先抵伤**，剩余伤害再扣血。
- **回合开始时清空自己未消耗的护盾**（参考杀戮尖塔）：玩家叠的盾用来挡怪物那一轮；怪物叠的盾用来挡你下一回合的攻击。
- 怪物 AI 交替行动：先攻击 10，再给自己叠盾 10，循环。
- 牌组为 5 张攻击 + 5 张防御。抽牌堆抽空后，弃牌堆洗回继续抽。

## 项目结构

```
src/main/java/com/roguelike/dungeon/
  App.java                 JavaFX 界面入口
  Launcher.java            启动器（推荐入口）
  game/
    CardType.java          攻击 / 防御两种卡牌
    Card.java              手牌实例
    Combat.java            战斗规则与回合循环
```

界面展示玩家血量与护盾、怪物血量与护盾（含下一动意图）、手牌按钮、战斗日志。

## 技术栈

- Java 21
- OpenJFX 21.0.11（`javafx-controls` / `javafx-graphics`）
- Maven（`javafx-maven-plugin`）
