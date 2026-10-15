package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pppopipupu.fxeasy.trade.Money;
import org.junit.jupiter.api.Test;

class FxUiTest {

    private static final ToIntFunction<String> WIDTH = text -> text.length() * 2;

    @Test
    void wrapBreaksAtWordBoundariesAndKeepsTheTail() {
        String body = "alpha beta gamma delta";
        List<String> lines = FxUi.wrap(body, 12, WIDTH);
        for (String line : lines) {
            assertTrue(WIDTH.applyAsInt(line) <= 12, "no line may be wider than the body: " + line);
        }
        assertEquals(body, String.join(" ", lines), "wrapping may not lose a word of the tail");
    }

    @Test
    void wrapCutsAWordThatIsLongerThanTheLine() {
        assertEquals(List.of("abc", "def", "ghi", "j"), FxUi.wrap("abcdefghij", 6, WIDTH));
    }

    /// Every time the terminal quotes a market tick - the bucket of the economy report, the feed of the event
    /// overlay, the announcement of one event - reads as a duration of play, not as a raw tick count.
    /// Every balance the panel prints goes through [FxUi#units], and a whole number of items is the case a player
    /// meets most: the trim has to drop the decimal point with the zeros, or 64 items of a currency would read as
    /// nothing at all.
    @Test
    void aUnitAmountReadsAsAnItemCount() {
        assertEquals("0", FxUi.units(0L));
        assertEquals("1", FxUi.units(Money.UNIT));
        assertEquals("64", FxUi.units(64L * Money.UNIT));
        assertEquals("1,000", FxUi.units(1_000L * Money.UNIT));
        assertEquals("2.5", FxUi.units(2L * Money.UNIT + Money.UNIT / 2L));
        assertEquals("64.961065", FxUi.units(64_961_065L));
        assertEquals("-3", FxUi.units(-3L * Money.UNIT));
    }

    @Test
    void aMarketTimeReadsInMinutesHoursAndDays() {
        assertEquals("0m", FxUi.marketTime(0L));
        assertEquals("0m", FxUi.marketTime(FxUi.TICKS_PER_MINUTE - 1L));
        assertEquals("1m", FxUi.marketTime(FxUi.TICKS_PER_MINUTE));
        assertEquals("59m", FxUi.marketTime(59L * FxUi.TICKS_PER_MINUTE));
        assertEquals("1h", FxUi.marketTime(60L * FxUi.TICKS_PER_MINUTE));
        assertEquals("2h 5m", FxUi.marketTime(125L * FxUi.TICKS_PER_MINUTE));
        assertEquals("1d", FxUi.marketTime(24L * 60L * FxUi.TICKS_PER_MINUTE));
        assertEquals("3d 4h", FxUi.marketTime((3L * 24L + 4L) * 60L * FxUi.TICKS_PER_MINUTE));
    }

    @Test
    void wrapOfAnEmptyBodyHasNoLines() {
        assertEquals(List.of(), FxUi.wrap("", 20, WIDTH));
    }

    /// The shop draws the picture of a tier as the backdrop of its card, and the file of a tier is a standing
    /// figure: what a card crops off a file that has another aspect than it, and a card that is wider than the
    /// figure is tall must keep the head at the top of the file instead of the middle of it.
    @Test
    void aCoverCropFillsTheAreaAndKeepsTheAnchoredEdge() {
        FxUi.Crop exact = FxUi.cover(400, 200, 400, 200, 0.5D);
        assertEquals(0.0F, exact.u0());
        assertEquals(1.0F, exact.u1());
        assertEquals(0.0F, exact.v0());
        assertEquals(1.0F, exact.v1(), "a file of the aspect of the area is neither cropped nor stretched");

        FxUi.Crop top = FxUi.cover(208, 246, 305, 720, 0.0D);
        assertEquals(0.0F, top.u0(), "a portrait wider than the card is cropped across its width");
        assertEquals(1.0F, top.u1());
        assertEquals(0.0F, top.v0(), "the top of the file is what the card anchors on");
        assertEquals(0.501F, top.v1(), 0.005F);

        FxUi.Crop bottom = FxUi.cover(208, 246, 305, 720, 1.0D);
        assertEquals(0.499F, bottom.v0(), 0.005F);
        assertEquals(1.0F, bottom.v1(), "anchoring the other way keeps the foot of the file");
        assertEquals(0.499F, FxUi.cover(208, 246, 305, 720, 2.0D).v0(), 0.005F,
                "an anchor past the foot of the file is clamped to the foot");
        assertEquals(0.501F, FxUi.cover(208, 246, 305, 720, -2.0D).v1(), 0.005F,
                "an anchor above the file is clamped to the top");
    }

    /// A file wider than the area is cropped across its width and not its height, so the anchor of a card that
    /// crops that way changes nothing, and a degenerate area or file falls back on the whole file.
    @Test
    void aCoverCropOfALandscapeKeepsTheStretchOutOfIt() {
        FxUi.Crop crop = FxUi.cover(200, 300, 400, 200, 0.0D);
        assertEquals(0.333F, crop.u0(), 0.005F);
        assertEquals(0.667F, crop.u1(), 0.005F);
        assertEquals(0.0F, crop.v0());
        assertEquals(1.0F, crop.v1());
        assertEquals(crop.u0(), FxUi.cover(200, 300, 400, 200, 1.0D).u0(), "the anchor only moves the cropped axis");
        FxUi.Crop degenerate = FxUi.cover(0, 10, 10, 10, 0.0D);
        assertEquals(0.0F, degenerate.u0());
        assertEquals(1.0F, degenerate.u1(), "an area with no size reads the whole file");
    }
}
