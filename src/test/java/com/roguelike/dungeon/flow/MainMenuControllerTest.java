package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.character.CharacterCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuControllerTest {

    @Test
    void selectingCharacterShouldCreateRunAndEnterGame() {
        MainMenuController menu = newMenu();

        menu.openCharacterSelection();
        GameController game = menu.startRun("warrior", 24680L, 2);

        assertEquals(MainMenuPhase.IN_RUN, menu.getPhase());
        assertSame(game, menu.getCurrentGame().orElseThrow());
        assertEquals(CharacterCatalog.WARRIOR,
                menu.getSelectedCharacter().orElseThrow());
        assertEquals(24680L, game.getRunState().getRunSeed());
        assertEquals(2, game.getRunState().getTotalActs());
        assertEquals(50, game.getRunState().getPlayer().getHealth());
        assertEquals(CardLibrary.startingDeck().size(), game.getRunState().getDeck().size());
        assertEquals(GamePhase.MAP, game.getPhase());
    }

    @Test
    void invalidSelectionShouldNotCreateOrChangeRun() {
        MainMenuController menu = newMenu();

        assertThrows(IllegalStateException.class,
                () -> menu.startRun("warrior", 1L, 1));
        menu.openCharacterSelection();
        assertThrows(IllegalArgumentException.class,
                () -> menu.startRun("missing", 1L, 1));

        assertEquals(MainMenuPhase.CHARACTER_SELECTION, menu.getPhase());
        assertTrue(menu.getCurrentGame().isEmpty());
        assertTrue(menu.getSelectedCharacter().isEmpty());
    }

    @Test
    void menuShouldSupportBackReturnAndExit() {
        MainMenuController menu = newMenu();

        menu.openCharacterSelection();
        menu.backToMainMenu();
        assertEquals(MainMenuPhase.MAIN_MENU, menu.getPhase());

        menu.openCharacterSelection();
        menu.startRun("warrior", 1L, 1);
        assertThrows(IllegalStateException.class, menu::exit);
        menu.returnToMainMenu();
        assertTrue(menu.getCurrentGame().isEmpty());

        menu.exit();
        assertEquals(MainMenuPhase.EXITED, menu.getPhase());
    }

    private static MainMenuController newMenu() {
        return new MainMenuController(
                CharacterCatalog.all(),
                List.of(CardLibrary.BASH),
                line -> { });
    }
}
