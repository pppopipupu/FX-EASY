package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// The ticket lays its rows out top down from the controls of the order and bottom up from the button, and no
/// row may land on another: the margin currency cells are the row under the label of the margin currency, the
/// leverage slider is the row under the label of the leverage, and a column that cannot hold the whole ticket
/// drops the margin currency cells and the notional readout instead of letting the amounts run into the button.
/// The layout counted the size row twice once, so the margin currency cells were drawn over the label of the
/// leverage.
class FxTicketLayoutTest {

    private static final int WIDTH = 200;

    private static ScreenRectangle column(int height) {
        return FxUi.rect(0, 0, WIDTH, height);
    }

    @Test
    void theMarginCurrencyIsTheRowUnderItsOwnLabel() {
        ScreenRectangle area = column(FxTicket.FULL_HEIGHT);
        ScreenRectangle lots = FxTicket.lotsArea(area);
        ScreenRectangle margins = FxTicket.marginAreas(area).getFirst();
        ScreenRectangle slider = FxTicket.leverageArea(area);
        assertEquals(Currencies.count(), FxTicket.marginAreas(area).size(), "the full ticket draws one cell per currency");
        assertTrue(lots.bottom() < margins.top(), "the size slider stands above the margin currency cells");
        assertEquals(lots.bottom() + FxTheme.GAP + FxTicket.TEXT_ROW, margins.top(),
                "the margin currency cells are the row under the label of the margin currency");
        assertEquals(margins.bottom() + FxTheme.GAP + FxTicket.TEXT_ROW, slider.top(),
                "the leverage slider is the row under the label of the leverage");
        assertTrue(slider.bottom() <= FxTicket.openArea(area).top(), "the slider stays above the button");
    }

    /// A column one row short of the whole ticket gets the compact layout, which drops the conveniences - the
    /// margin currency cells and the notional readout - and never lets what it still draws run into the button:
    /// the two sliders and the amounts the order is made of stay above it.
    @Test
    void aCompactColumnDropsTheConveniencesInsteadOfOverlappingTheButton() {
        ScreenRectangle compact = column(FxTicket.FULL_HEIGHT - 1);
        assertTrue(FxTicket.marginAreas(compact).isEmpty(), "a column that cannot hold the whole ticket drops them");
        int amounts = FxTicket.leverageArea(compact).bottom() + 2 * FxTicket.TEXT_ROW;
        assertTrue(amounts <= FxTicket.openArea(compact).top(), "the amounts the compact ticket keeps stay above the button");
        assertTrue(FxTicket.lotsArea(compact).bottom() < FxTicket.openArea(compact).top(),
                "the compact ticket keeps the size slider");
    }

    /// The size slider runs from nothing at its left end to the whole range the margin backs at its right one, so
    /// the handle of a size in between stands at the share of that range the size takes.
    @Test
    void theHandleOfTheSizeSliderStandsAtItsShareOfTheRange() {
        assertEquals(0.0D, FxTicket.lotsFraction(0, 100));
        assertEquals(0.25D, FxTicket.lotsFraction(25, 100));
        assertEquals(1.0D, FxTicket.lotsFraction(100, 100));
        assertEquals(1.0D, FxTicket.lotsFraction(400, 100), "a size above the range is drawn at its right end");
        assertEquals(0.0D, FxTicket.lotsFraction(50, 0), "an account whose margin backs nothing shows an empty slider");
        assertEquals(0, FxTicket.lotsAt(0.0D, 100));
        assertEquals(25, FxTicket.lotsAt(0.25D, 100));
        assertEquals(100, FxTicket.lotsAt(1.0D, 100));
        assertEquals(100, FxTicket.lotsAt(2.0D, 100), "a drag past the right end stops at the range");
        assertEquals(0, FxTicket.lotsAt(0.5D, 0));
    }
}
