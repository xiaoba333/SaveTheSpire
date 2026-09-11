package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.character.CharacterCatalog;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.Objects;

/**
 * 开局菜单：管理主菜单 / 选角阶段，并把选中的角色交给 {@link RunFactory} 开局。
 *
 * <p>只依赖 {@link CharacterCatalog}，不写死具体角色。选角流程运行在
 * {@link MenuPhase}，正式开局后才进入局内的 {@link GamePhase}。</p>
 */
public final class MenuController {

    private final CharacterCatalog catalog;
    private MenuPhase phase = MenuPhase.MAIN_MENU;

    public MenuController(CharacterCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "角色目录不能为 null");
    }

    public MenuPhase getPhase() {
        return phase;
    }

    /** 进入选角阶段。 */
    public void beginCharacterSelect() {
        requirePhase(MenuPhase.MAIN_MENU);
        phase = MenuPhase.CHARACTER_SELECT;
    }

    /** 列出当前可选角色（只读，顺序即展示顺序）。 */
    public List<CharacterDefinition> getAvailableCharacters() {
        return catalog.getAvailableCharacters();
    }

    /** 校验并选中一个角色。 */
    public CharacterDefinition selectCharacter(String characterId) {
        requirePhase(MenuPhase.CHARACTER_SELECT);
        return catalog.getById(characterId);
    }

    /**
     * 用选中的角色开一局。
     *
     * @param characterId 选中的角色编号
     * @param runSeed 本局随机种子
     * @param totalActs 总章节数
     * @return 本局权威 RunState
     * @throws IllegalArgumentException 角色编号不存在
     */
    public RunState createRun(String characterId, long runSeed, int totalActs) {
        requirePhase(MenuPhase.CHARACTER_SELECT);
        RunState runState = RunFactory.createRun(catalog, characterId, runSeed, totalActs);
        phase = MenuPhase.IN_RUN;
        return runState;
    }

    /** 退出菜单。 */
    public void exit() {
        phase = MenuPhase.EXITED;
    }

    private void requirePhase(MenuPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "当前菜单阶段为 " + phase + "，期望阶段为 " + expected);
        }
    }
}
