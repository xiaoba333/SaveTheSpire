第一层「地牢外围」怪物立绘放置说明
=====================================

命名规则：文件名 = MonsterDefinition 的 spriteKey + .png

需要放进去的文件（照抄文件名即可，缺哪个就回落 ../placeholder.png）：

  grub.png              蛆（设计案里的「两条蛆」，出场两只共用这一张）
  wraith.png            亡灵
  skeleton.png          骷髅
  explorer_male.png     探险者男
  explorer_female.png   探险者女
  giant_remains.png     巨人遗骸
  kairos_egg_1.png      凯洛斯的蛋（无暇）
  kairos_egg_2.png      凯洛斯的蛋（轻微破裂）
  kairos_egg_3.png      凯洛斯的蛋（中度破裂）
  kairos_egg_4.png      凯洛斯的蛋（几乎破裂）
  kairos.png            凯洛斯

尺寸建议：宽度 512px 左右，透明背景 PNG，底部对齐（怪物站在同一条基线上）。

UI 侧加载方式（不要自己拼路径，走这个 API 才能享受兜底）：

    String path = monster.spriteOrDefault();
    Image image = new Image(MonsterSprite.class.getResourceAsStream(path));

注意：意图小图标（intent_attack / intent_debuff 等）是另一套资源，不放在这里。
      它们的键名见 Intents 类里的 ICON_* 常量。
