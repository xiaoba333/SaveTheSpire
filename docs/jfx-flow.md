# JavaFX 总流程界面

简易图形客户端，覆盖标题、选角、地图、战斗、奖励、事件、篝火、商店、胜负。
只调用 `MenuController` / `GameController`，不改核心规则。

启动后先进入标题页：

- **探索高塔**：进入选角
- **怯懦离开**：退出游戏

## 启动

```powershell
.\mvnw.cmd javafx:run
```

或运行 `com.roguelike.dungeon.Launcher`。

纯战斗 Demo 仍是 `com.roguelike.dungeon.App`，不会随 `javafx:run` 启动。

## 操作摘要

- 选角：点角色按钮，或输入隐藏角色编号（如 `god`）后点「用编号开局」
- 地图：点可选节点
- 战斗：把牌拖向怪物或拖出手牌区打出；锻造拖到目标牌上。攻击会播放刀光和震动。没有牌面的卡会显示文字说明。
- 奖励：领取一张牌或跳过
- 事件 / 篝火 / 商店：点对应按钮，商店结束后点「离开商店」
