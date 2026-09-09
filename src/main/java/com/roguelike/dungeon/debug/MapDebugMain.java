package com.roguelike.dungeon.debug;

import com.roguelike.dungeon.game.map.MapService;
import com.roguelike.dungeon.game.map.MapTextRenderer;

/** 在控制台中查看地图生成结果的独立调试入口。 */
public final class MapDebugMain {
    private static final long DEFAULT_SEED = 12345L;

    private MapDebugMain() {
    }

    public static void main(String[] args) {
        long seed = readSeed(args);
        MapService mapService = new MapService(seed);

        System.out.println("地图种子：" + seed);
        System.out.print(new MapTextRenderer().render(mapService));
    }

    private static long readSeed(String[] args) {
        if (args.length == 0) {
            return DEFAULT_SEED;
        }
        try {
            return Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("地图种子必须是整数: " + args[0], exception);
        }
    }
}
