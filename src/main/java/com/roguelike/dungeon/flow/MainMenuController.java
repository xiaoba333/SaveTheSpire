package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.character.CharacterDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 不依赖具体 UI 的主菜单和角色选择控制器。
 *
 * <p>Unity 可以读取 {@link #getPhase()} 和 {@link #getCharacters()}，再调用
 * 打开选角、选择角色、返回或退出等方法。</p>
 */
public final class MainMenuController {
    private final List<CharacterDefinition> characters;
    private final List<Card> rewardPool;
    private final Consumer<String> combatLogger;

    private MainMenuPhase phase = MainMenuPhase.MAIN_MENU;
    private CharacterDefinition selectedCharacter;
    private GameController currentGame;

    public MainMenuController(
            List<CharacterDefinition> characters,
            List<Card> rewardPool,
            Consumer<String> combatLogger) {
        this.characters = List.copyOf(Objects.requireNonNull(
                characters, "角色列表不能为 null"));
        if (this.characters.isEmpty()) {
            throw new IllegalArgumentException("角色列表不能为空");
        }
        this.characters.forEach(character -> Objects.requireNonNull(
                character, "角色列表不能包含 null"));
        long distinctIds = this.characters.stream()
                .map(CharacterDefinition::id)
                .distinct()
                .count();
        if (distinctIds != this.characters.size()) {
            throw new IllegalArgumentException("角色列表不能包含重复编号");
        }

        this.rewardPool = List.copyOf(Objects.requireNonNull(
                rewardPool, "奖励卡池不能为 null"));
        this.rewardPool.forEach(card -> Objects.requireNonNull(
                card, "奖励卡池不能包含 null"));
        this.combatLogger = Objects.requireNonNull(
                combatLogger, "战斗日志处理器不能为 null");
    }

    public MainMenuPhase getPhase() {
        return phase;
    }

    public List<CharacterDefinition> getCharacters() {
        return characters;
    }

    public Optional<CharacterDefinition> getSelectedCharacter() {
        return Optional.ofNullable(selectedCharacter);
    }

    public Optional<GameController> getCurrentGame() {
        return Optional.ofNullable(currentGame);
    }

    /** 从主菜单进入角色选择。 */
    public void openCharacterSelection() {
        requirePhase(MainMenuPhase.MAIN_MENU);
        phase = MainMenuPhase.CHARACTER_SELECTION;
    }

    /** 从角色选择返回主菜单。 */
    public void backToMainMenu() {
        requirePhase(MainMenuPhase.CHARACTER_SELECTION);
        selectedCharacter = null;
        phase = MainMenuPhase.MAIN_MENU;
    }

    /**
     * 选定角色并创建新的一局游戏。
     *
     * @return 已创建的游戏主流程控制器
     */
    public GameController startRun(String characterId, long runSeed, int totalActs) {
        requirePhase(MainMenuPhase.CHARACTER_SELECTION);
        CharacterDefinition character = characters.stream()
                .filter(candidate -> candidate.id().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知角色编号: " + characterId));

        GameController game = new GameController(
                character.createRunState(runSeed, totalActs),
                rewardPool,
                combatLogger);
        selectedCharacter = character;
        currentGame = game;
        phase = MainMenuPhase.IN_RUN;
        return game;
    }

    /** 放弃当前一局并返回主菜单。 */
    public void returnToMainMenu() {
        requirePhase(MainMenuPhase.IN_RUN);
        currentGame = null;
        selectedCharacter = null;
        phase = MainMenuPhase.MAIN_MENU;
    }

    /** 退出主菜单流程。 */
    public void exit() {
        if (phase == MainMenuPhase.IN_RUN) {
            throw new IllegalStateException("游戏进行中不能直接退出主菜单流程");
        }
        phase = MainMenuPhase.EXITED;
    }

    private void requirePhase(MainMenuPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "当前菜单阶段是 " + phase + "，不能执行仅限 " + expected + " 的操作");
        }
    }
}
