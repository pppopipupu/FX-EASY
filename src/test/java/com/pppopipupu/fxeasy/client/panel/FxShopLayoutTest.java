package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.Membership;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.Identifier;

/// The shop sells its tiers side by side, each card drawing the picture of its own character as the backdrop the
/// whole of the tier is written over - the name, the lore, the price of a period and its length, then what the
/// tier buys - and manages the subscription in the bar under the cards, and no cell may land on another whatever
/// the panel is: the header, the detail and the button of a tier stand inside their own card without running into
/// each other, and neither the cards nor the bar may run into each other. The card is the tallest thing the panel
/// draws, so a page laid out from the top of its area alone would push the button of every card past the bottom
/// of the shortest panel the terminal draws.
class FxShopLayoutTest {

    private static final LoungeFile.Pool POOL = new LoungeFile.Pool(24_000L, 10, List.of(
            new LoungeFile.Entry(Identifier.parse("minecraft:diamond"), 1, 1, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:apple"), 2, 3, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:not_a_real_item"), 1, 8, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:emerald"), 1, 0, Optional.empty())));

    /// The list area of the shortest panel the terminal draws: 427 by 240 design units, which is the smallest
    /// window the panel is laid out for.
    private static ScreenRectangle smallest() {
        return FxTerminalLayout.of(427, 240, 40).list();
    }

    private static ScreenRectangle page(int width, int height) {
        return FxUi.rect(0, 0, width, height);
    }

    @Test
    void everyCardHoldsItsHeaderItsDetailAndItsButton() {
        for (ScreenRectangle area : List.of(page(640, 325), smallest())) {
            List<FxShop.CardCells> cards = FxShop.cards(area);
            assertEquals(Membership.plans().size(), cards.size(), "the page sells every plan of the catalogue");
            for (int index = 0; index < cards.size(); index++) {
                FxShop.CardCells cells = cards.get(index);
                assertEquals(Membership.plans().get(index), cells.plan(), "the cards follow the catalogue");
                inside("the card of " + cells.plan().id(), cells.card(), area);
                inside("the name of " + cells.plan().id(), cells.name(), cells.card());
                inside("the lore of " + cells.plan().id(), cells.lore(), cells.card());
                inside("the price of " + cells.plan().id(), cells.price(), cells.card());
                inside("the period of " + cells.plan().id(), cells.period(), cells.card());
                inside("the detail of " + cells.plan().id(), cells.detail(), cells.card());
                inside("the button of " + cells.plan().id(), cells.action(), cells.card());
                assertTrue(cells.name().bottom() <= cells.lore().top(), "the name stays above the lore");
                assertTrue(cells.lore().bottom() < cells.price().top(), "the lore stays above the price");
                assertTrue(cells.price().bottom() <= cells.period().top(), "the price stays above its period");
                assertTrue(cells.period().bottom() < cells.detail().top(), "the detail stands under the period");
                assertTrue(cells.detail().bottom() <= cells.action().top(), "the detail stays above the button");
                assertTrue(cells.detail().height() > 0, "a card the terminal is laid out for carries a detail");
                if (index > 0) {
                    assertTrue(cards.get(index - 1).card().right() <= cells.card().left(),
                            "the cards of the page never overlap");
                }
            }
        }
    }

    @Test
    void theBarStandsUnderEveryCard() {
        for (ScreenRectangle area : List.of(page(640, 325), smallest())) {
            List<FxShop.CardCells> cards = FxShop.cards(area);
            ScreenRectangle footer = FxShop.footerArea(area);
            FxShop.FooterCells cells = FxShop.footerCells(footer);
            inside("the bar", footer, area);
            inside("the renewal switch", cells.renew(), footer);
            inside("the lounge entry", cells.lounge(), footer);
            assertTrue(cards.getLast().card().bottom() <= footer.top(), "the bar stands under every card");
            assertTrue(cells.renew().right() < cells.lounge().left(), "the bar is drawn right to left");
        }
    }

    /// A page too short for a card of the height the panel lays out keeps the button of every card inside the
    /// area rather than pushing it past the bar: the wording of a card is not negotiable, and a card that would
    /// hang over the bar must not be drawn taller than the page can hold.
    @Test
    void aPageTooShortKeepsTheButtonOfEveryCardInsideIt() {
        ScreenRectangle area = page(640, 150);
        ScreenRectangle footer = FxShop.footerArea(area);
        for (FxShop.CardCells cells : FxShop.cards(area)) {
            inside("the name of " + cells.plan().id(), cells.name(), cells.card());
            inside("the button of " + cells.plan().id(), cells.action(), cells.card());
            assertTrue(cells.card().bottom() <= footer.top(), "a card never runs into the bar");
        }
    }

    /// What the page weighs the share of a gift against is what the lounge may actually draw: an item this build
    /// cannot hand over and an entry the world gave no weight to are never delivered, so they must not take a
    /// share of the draws either.
    @Test
    void theShareOfAGiftIsItsWeightAgainstWhatTheLoungeCanDraw() {
        assertEquals(2, FxShop.usable(POOL), "only the gifts the lounge can hand over are written out");
        assertEquals(4, FxShop.weight(POOL), "an undrawable gift takes no share of the draws");
        assertEquals(25.0D, FxShop.share(1, FxShop.weight(POOL)), 1.0E-9D, "the diamond takes a quarter");
        assertEquals(75.0D, FxShop.share(3, FxShop.weight(POOL)), 1.0E-9D, "the apples take the rest");
        assertEquals(0.0D, FxShop.share(1, 0), 1.0E-9D, "a pool nothing can be drawn from weighs nothing");
    }

    /// The page draws into the area it is handed, so every cell of it has to stand inside that area: a card that
    /// hangs out of it is drawn over the bar of the notice or past the edge of the panel.
    private static void inside(String what, ScreenRectangle inner, ScreenRectangle outer) {
        assertTrue(inner.left() >= outer.left() && inner.top() >= outer.top() && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom(),
                what + " spans " + inner.left() + "," + inner.top() + " to " + inner.right() + "," + inner.bottom()
                        + ", outside " + outer.left() + "," + outer.top() + " to " + outer.right() + ","
                        + outer.bottom());
    }
}
