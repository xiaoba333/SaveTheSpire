package com.roguelike.dungeon.game.enemy;

/**
 * 立绘资源定位：把怪物定义换算成 JavaFX 能直接加载的资源路径。
 *
 * <p>约定目录结构（放在 {@code src/main/resources} 下）：</p>
 * <pre>
 * src/main/resources/monster/
 * ├── placeholder.png          # 缺图时的兜底图
 * ├── act1/
 * │   ├── grub.png             # 两条蛆
 * │   ├── wraith.png           # 亡灵
 * │   ├── skeleton.png         # 骷髅
 * │   ├── explorer_male.png    # 探险者男
 * │   ├── explorer_female.png  # 探险者女
 * │   ├── giant_remains.png    # 巨人遗骸
 * │   ├── kairos_egg_1..4.png  # 凯洛斯的蛋（四段形态）
 * │   └── kairos.png           # 凯洛斯
 * └── act2/
 *     └── ...
 * </pre>
 *
 * <p>文件名的规则很简单：<b>怪物定义的 spriteKey + .png</b>。
 * 立绘还没到位时不用改任何代码——UI 调用 {@link #pathOrDefault} 会自动回落到兜底图。</p>
 *
 * <p>本类只做字符串拼接，不依赖 JavaFX，方便在没有图形环境时（跑单元测试、跑 Demo）使用。</p>
 */
public final class MonsterSprite {

    /** 资源根目录。 */
    public static final String ROOT = "/monster/";

    /** 缺图时的兜底立绘。 */
    public static final String PLACEHOLDER = ROOT + "placeholder.png";

    private MonsterSprite() {
    }

    /** 怪物定义的立绘路径，例如 {@code /monster/act1/skeleton.png}。 */
    public static String pathOf(MonsterDefinition definition) {
        return pathOf(definition.spriteKey());
    }

    /** 按 spriteKey 取立绘路径。 */
    public static String pathOf(String spriteKey) {
        return ROOT + spriteKey + ".png";
    }

    /** 按怪物 ID 取立绘路径（仅当 spriteKey 与 id 相同时才正确）。 */
    public static String pathOfId(String monsterId) {
        return ROOT + monsterId + ".png";
    }

    /**
     * 取立绘路径；若该资源在 classpath 里不存在则回落到兜底图。
     * UI 层请优先使用这个方法，避免因为策划还没交图而抛 NPE。
     */
    public static String pathOrDefault(MonsterDefinition definition) {
        String path = pathOf(definition);
        return exists(path) ? path : PLACEHOLDER;
    }

    /** 判断 classpath 里是否存在该资源。 */
    public static boolean exists(String resourcePath) {
        return MonsterSprite.class.getResource(resourcePath) != null;
    }
}
