package com.roguelike.dungeon.ui.map;

import com.roguelike.dungeon.game.map.DungeonMap;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapProgress;

import java.util.Objects;
import java.util.function.Consumer;

/** 协调地图进度、地图页面以及后续关卡页面。 */
public final class MapController {
    private final DungeonMap dungeonMap;
    private final MapProgress progress;
    private final Consumer<MapNode> levelStarter;
    private final MapView view;

    public MapController(DungeonMap dungeonMap, Consumer<MapNode> levelStarter) {
        this.dungeonMap = Objects.requireNonNull(dungeonMap, "地图不能为 null");
        this.progress = new MapProgress(dungeonMap);
        this.levelStarter = Objects.requireNonNull(levelStarter, "关卡启动器不能为 null");
        this.view = new MapView(dungeonMap, progress, this::selectNode);
    }

    /** 选择一个当前可达的节点，并将节点交给具体关卡模块。 */
    public boolean selectNode(int nodeId) {
        if (!progress.canEnter(nodeId)) {
            return false;
        }
        MapNode node = progress.enter(nodeId);
        view.refresh();
        levelStarter.accept(node);
        return true;
    }

    /** 完成当前关卡、解锁后续路线并刷新地图。 */
    public MapNode completeCurrentNode() {
        MapNode completedNode = progress.completeCurrentNode();
        view.refresh();
        return completedNode;
    }

    public MapView getView() {
        return view;
    }

    public DungeonMap getDungeonMap() {
        return dungeonMap;
    }

    public MapProgress getProgress() {
        return progress;
    }
}
