package com.roguelike.dungeon;

/**
 * 非 JavaFX Application 的启动类，避免模块路径下直接运行 Application 子类失败。
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
