package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.shop.Membership;
import net.minecraft.util.ARGB;

/// What a card hands the effect travels in the channels of the vertex colour, because the GUI blit path carries
/// nothing else per draw: the tier picks the effect, the slice of the file the card shows is where the effect
/// samples the picture it masks itself with, and the intensity is how strongly it is drawn. A figure packed into
/// the wrong channel would raise the wrong effect on a card or stretch its sweep across the whole file rather
/// than the card.
class FxShopCardTest {

    /// `core/shop_card` names its three effects by the place of a plan in the catalogue, and the panel sends
    /// that place, so the order the shop lays its cards out in is what decides which effect a card carries.
    @Test
    void theCataloguePutsTheTiersWhereTheShaderExpectsThem() {
        assertEquals(0, Membership.plans().indexOf(Membership.GO), "Go carries the first effect of the shader");
        assertEquals(1, Membership.plans().indexOf(Membership.PLUS), "Plus carries the second effect");
        assertEquals(2, Membership.plans().indexOf(Membership.PRO), "Pro carries the third effect");
    }

    @Test
    void everyFigureOfAnEffectFitsTheChannelItTravelsIn() {
        int tint = FxShopCard.tint(1, 0.6789F, 0.35F);
        assertEquals(1, ARGB.red(tint), "the tier is the red channel");
        assertEquals(0.6789F, ARGB.green(tint) / 255.0F, 1.0F / 255.0F, "the slice is the green channel");
        assertEquals(0.35F, ARGB.blue(tint) / 255.0F, 1.0F / 255.0F, "the intensity is the blue channel");
        assertEquals(255, ARGB.alpha(tint), "the effect is laid over the card without fading what stands under it");
    }

    @Test
    void aFigureOutsideItsChannelIsClampedRatherThanWrapped() {
        int tint = FxShopCard.tint(700, 2.0F, -1.0F);
        assertEquals(255, ARGB.red(tint), "a tier past the last one stays at the top of its channel");
        assertEquals(255, ARGB.green(tint), "a slice larger than the file stays at the top of its channel");
        assertEquals(0, ARGB.blue(tint), "a negative intensity stays at the bottom of its channel");
    }
}
