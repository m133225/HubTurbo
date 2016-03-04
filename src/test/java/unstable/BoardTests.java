package unstable;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.loadui.testfx.controls.Commons.hasText;
import static ui.components.KeyboardShortcuts.*;

import backend.Logic;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import guitests.UITest;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import prefs.Preferences;
import ui.TestController;
import ui.UI;
import ui.issuepanel.PanelControl;
import util.HTLog;
import util.PlatformEx;
import util.Utility;

public class BoardTests extends UITest {

    private static final Logger logger = HTLog.get(Logic.class);
    /**
     * The initial state is one panel with no filter, and no saved boards
     */
    private static void reset() {
        UI ui = TestController.getUI();
        ui.getPanelControl().closeAllPanels();
        ui.getPanelControl().createNewPanelAtStart();
        UI.prefs.clearAllBoards();
        ui.updateTitle();
    }

    @Before
    public void before() {
        PlatformEx.runAndWait(BoardTests::reset);
    }

    @Test
    public void boards_panelCount_boardsSaveSuccessfully() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        saveBoardWithName("Board 1");

        waitAndAssertEquals(1, panelControl::getNumberOfSavedBoards);
        assertEquals(1, panelControl.getPanelCount());
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("Board 1"));
    }

    private void saveBoardWithName(String name) {
        traverseMenu("Boards", "Save as");
        waitUntilNodeAppears("#boardnameinput");
        logger.info("Board name text field found.");
        logger.info("Board name text field is in focus: " + find("#boardnameinput").isFocused());
        ((TextField) find("#boardnameinput")).setText(name);
        click("OK");
    }


    private static String getUiTitleWithOpenBoard(String boardName) {
        String version = Utility.version(UI.VERSION_MAJOR, UI.VERSION_MINOR, UI.VERSION_PATCH);
        return String.format(UI.WINDOW_TITLE, version, boardName);
    }

}
