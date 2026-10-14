package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.client.FxLounge;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// The lounge hands its gifts over one row at a time and scrolls what its card cannot hold: the card of the
/// smallest panel still holds a row and the cell that closes it, every claim cell it draws stands inside its
/// list, and a gift under the window of that list - or an index past the end of what the account holds - is no
/// cell of this frame, so a scripted click at it reports that it found none instead of claiming a present the
/// player never aimed at.
class FxLoungeLayoutTest {

    @Test
    void theCardOfTheSmallestPanelHoldsItsListAndItsCloseCell() {
        int width = 427;
        int height = 240;
        ScreenRectangle card = FxLounge.card(width, height);
        ScreenRectangle list = FxLounge.listArea(width, height);
        ScreenRectangle close = FxLounge.closeArea(width, height);
        inside("the card", card, FxUi.rect(0, 0, width, height));
        inside("the list", list, card);
        inside("the close cell", close, card);
        assertTrue(FxLounge.rows(width, height) >= 1, "the smallest card still shows a row of the lounge");
    }

    @Test
    void theRowsTheCardShowsAreTheCellsItDraws() {
        int width = 640;
        int height = 360;
        FxLounge lounge = new FxLounge();
        int rows = FxLounge.rows(width, height);
        ScreenRectangle list = FxLounge.listArea(width, height);
        for (int index = 0; index < rows; index++) {
            ScreenRectangle claim = lounge.claimArea(index, rows + 4, width, height);
            assertNotNull(claim, "row " + index + " is inside the window of the list");
            inside("the claim cell of row " + index, claim, list);
        }
        assertNull(lounge.claimArea(rows, rows + 4, width, height), "a row under the window is not drawn");
        assertNull(lounge.claimArea(0, 0, width, height), "a lounge holding nothing draws no row to claim from");
        assertNull(lounge.claimArea(9, 3, width, height), "an index past the end of the lounge is no gift");
        int shorter = height / 2;
        int fewer = FxLounge.rows(width, shorter);
        assertTrue(fewer < rows, "a shorter panel shows fewer rows of the lounge");
        assertNotNull(lounge.claimArea(fewer - 1, rows + 4, width, shorter), "the last row it still shows is drawn");
        assertNull(lounge.claimArea(fewer, rows + 4, width, shorter), "the first row under it is not");
    }

    private static void inside(String what, ScreenRectangle inner, ScreenRectangle outer) {
        assertTrue(inner.left() >= outer.left() && inner.top() >= outer.top() && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom(),
                what + " spans " + inner.left() + "," + inner.top() + " to " + inner.right() + "," + inner.bottom()
                        + ", outside " + outer.left() + "," + outer.top() + " to " + outer.right() + ","
                        + outer.bottom());
    }
}
