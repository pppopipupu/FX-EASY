package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.market.Timeframe;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// Every cell of the row under the header is clickable, so no two of them may ever overlap: a click that lands on
/// the neighbour of the cell it aimed at changes something the player did not ask for, and one of them - the
/// display currency - changes what every figure of every page means. The cells shrink and the chart settings are
/// dropped long before that, and the row is laid out for windows far narrower than the panel is ever drawn in,
/// which is where the page tabs once pushed the intel entry into the display currency.
class FxControlRowLayoutTest {

    /// Narrowest row the test lays out, in design units, which is half the narrowest window the panel can be
    /// given (the frame is divided by two).
    private static final int NARROWEST = 160;
    private static final int WIDEST = 1_600;

    /// The control row of a frame [width] wide, which is the rectangle the screen lays the row out in: one unit
    /// taller than a cell, because the cells stand a unit inside it.
    private static ScreenRectangle row(int width) {
        return FxTerminalLayout.of(width, 360, 40).controls();
    }

    @Test
    void theCellsOfTheRowNeverRunIntoEachOther() {
        for (int width = NARROWEST; width <= WIDEST; width++) {
            for (boolean chartCells : List.of(false, true)) {
                FxControlRow.Geometry geometry = FxControlRow.geometry(row(width), chartCells);
                String where = " at width " + width + (chartCells ? " on a chart page" : " on a page without one");
                int right = inside(geometry.area(), FxControlRow.cell(geometry, 0), "the first page tab" + where);
                for (int index = 1; index <= FxTab.values().length; index++) {
                    right = inside(geometry.area(), FxControlRow.cell(geometry, index),
                            "cell " + index + " of the row" + where, right);
                }
                ScreenRectangle currency = FxControlRow.basisCell(geometry);
                right = inside(geometry.area(), currency, "the display currency cell" + where, right);
                for (int index = 0; index < Timeframe.ALL.size(); index++) {
                    ScreenRectangle box = FxControlRow.timeframeCell(geometry, index);
                    if (box == null) {
                        break;
                    }
                    right = inside(geometry.area(), box, "timeframe cell " + index + where, right);
                }
                for (int index = 0; index < FxClientState.ChartStyle.values().length; index++) {
                    ScreenRectangle box = FxControlRow.chartStyleCell(geometry, index);
                    if (box == null) {
                        break;
                    }
                    right = inside(geometry.area(), box, "chart style cell " + index + where, right);
                }
                assertTrue(right <= geometry.area().right(), "the cells of the row run past its right edge" + where);
            }
        }
    }

    /// Asserts that [box] is inside [area] and that it starts after [left], and answers where it ends. The first
    /// cell of the row is the only one with nothing to its left, so it is checked without a neighbour.
    private static int inside(ScreenRectangle area, ScreenRectangle box, String what, int left) {
        assertTrue(box.left() >= left, what + " starts at " + box.left() + ", before " + left);
        return inside(area, box, what);
    }

    private static int inside(ScreenRectangle area, ScreenRectangle box, String what) {
        assertNotNull(box, what + " is not drawn at all");
        assertTrue(box.left() >= area.left() && box.right() <= area.right(), what + " leaves the row it belongs to");
        assertTrue(box.top() >= area.top() && box.bottom() <= area.bottom(), what + " leaves the row it belongs to");
        return box.right();
    }
}
