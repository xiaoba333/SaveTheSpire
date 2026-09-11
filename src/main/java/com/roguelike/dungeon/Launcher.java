package com.roguelike.dungeon;

import com.roguelike.dungeon.ui.FlowApp;

/**
 * 非 JavaFX Application 的启动类，避免模块路径下直接运行 Application 子类失败。
 *
 * <p>默认进入覆盖选角到通关的简易总流程。纯战斗 Demo 请运行 {@link App}。</p>
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        FlowApp.main(args);
    }
}
