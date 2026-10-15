package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.Membership;
import net.minecraft.resources.Identifier;

/// What the shop page writes out of the pool the lounge of the world draws from. The pool is the world's list of
/// gifts and not the page's, so a gift this build cannot hand over is left out rather than drawn as a line the
/// lounge would never deliver, and the shares of what is left are weighed against the rest of the pool. A gift
/// itself is named by the stack the lounge would hand over, which only a running client can build, so the lines
/// of the pool are drawn there and everything around them is decided here.
class FxShopPoolTest {

    /// The place the four costs of the platform stand at in the detail of a card, in the order the tier stacks
    /// them: the spread, the slippage, the swap and the conversion, under the ceiling on the leverage and the
    /// heading of the group.
    private static final int SPREAD = 2;
    private static final int CONVERSION = 5;

    private static final LoungeFile.Pool POOL = new LoungeFile.Pool(24_000L, 10, List.of(
            new LoungeFile.Entry(Identifier.parse("minecraft:diamond"), 1, 1, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:apple"), 2, 3, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:not_a_real_item"), 1, 8, Optional.empty()),
            new LoungeFile.Entry(Identifier.parse("minecraft:emerald"), 1, 0, Optional.empty())));

    /// A pool that holds nothing the lounge could hand over writes no gift at all, whatever the file says: a
    /// card that listed an item this build cannot resolve would advertise a lounge that can only ever be empty.
    @Test
    void aPoolWithoutAGiftTheLoungeCanHandOverWritesNothing() {
        LoungeFile.Pool unusable = new LoungeFile.Pool(24_000L, 10, List.of(
                new LoungeFile.Entry(Identifier.parse("minecraft:not_a_real_item"), 1, 8, Optional.empty()),
                new LoungeFile.Entry(Identifier.parse("minecraft:emerald"), 1, 0, Optional.empty())));
        assertEquals(0, FxShop.usable(unusable), "the page counts no gift of this pool");
        assertEquals(0, FxShop.weight(unusable), "and weighs nothing against the shares of its gifts");
        assertTrue(FxShop.poolLines(Membership.PRO, unusable).isEmpty(), "the page writes no gift of it out");
    }

    /// Only the tier that carries a lounge writes a pool out, and a pool the platform has not reported yet
    /// leaves that tier with its lounge line alone rather than with gifts of a world it may not be connected to.
    @Test
    void onlyTheTierThatCarriesALoungeWritesThePoolOut() {
        assertTrue(FxShop.poolLines(Membership.PLUS, POOL).isEmpty(), "Plus carries no lounge");
        assertTrue(FxShop.poolLines(Membership.GO, POOL).isEmpty(), "Go carries no lounge");
        assertTrue(FxShop.poolLines(Membership.PRO, null).isEmpty(), "a pool that never arrived is no pool");
    }

    /// A card that cannot hold both what its tier buys and the whole pool of its lounge drops the notes that
    /// explain the costs of the platform rather than the pool: the pool is the detail the card was bought to
    /// show, and a note that would push a gift off the card is worth less than the gift.
    @Test
    void aCardTooShortForTheNotesKeepsItsPool() {
        FxShop.Detail roomy = FxShop.detail(Membership.PRO, POOL, 400);
        FxShop.Detail tight = FxShop.detail(Membership.PRO, POOL, 100);
        assertTrue(roomy.lines().size() > tight.lines().size(), "a card with the room explains every cost");
        assertEquals(roomy.entries(), tight.entries(), "the pool is written whatever the room the card has");
        assertEquals(2, tight.entries(), "the pool of the world is what the card writes out");
        assertEquals(0, FxShop.detail(Membership.PLUS, POOL, 400).entries(), "a tier without a lounge has none");
    }

    /// The lounge line of the top tier carries the pace of the pool the world runs, a pool that has not arrived
    /// yet is written as such rather than as a lounge the tier does not carry, and a tier that carries no lounge
    /// says so whatever pool the world runs.
    @Test
    void theLoungeLineFollowsThePoolOfTheWorld() {
        assertEquals(FxText.get("fxeasy.shop.lounge.rate", FxUi.marketTime(24_000L)).getString(),
                value(FxShop.lines(Membership.PRO, POOL, false), "fxeasy.shop.lounge"),
                "the pace of the pool of the world is what the card writes");
        assertEquals(FxText.get("fxeasy.shop.lounge.pending").getString(),
                value(FxShop.lines(Membership.PRO, null, false), "fxeasy.shop.lounge"),
                "a pool that has not arrived is not a lounge");
        assertEquals(FxText.get("fxeasy.shop.lounge.none").getString(),
                value(FxShop.lines(Membership.PLUS, POOL, false), "fxeasy.shop.lounge"),
                "a tier without a lounge carries none");
    }

    /// What a tier writes about the costs of the platform is the discount that tier itself carries, so a tier
    /// that is repriced cannot leave its card advertising the share it used to take off.
    @Test
    void theFeeLinesCarryTheDiscountOfThePlan() {
        assertEquals(List.of(off(33), off(33), off(33), standard()), feeValues(Membership.PLUS),
                "Plus takes a third off the spread, the slippage and the swap and nothing off the conversion");
        assertEquals(List.of(off(66), off(66), off(66), off(66)), feeValues(Membership.PRO),
                "Pro takes two thirds off every cost");
        assertEquals(List.of(standard(), standard(), standard(), standard()), feeValues(Membership.GO),
                "Go takes nothing off anything");
    }

    /// The ceiling on the leverage a card writes is the one the plan itself carries, which is what the ticket
    /// of the account reads through the same plan.
    @Test
    void theLeverageLineCarriesTheCeilingOfThePlan() {
        for (Membership plan : Membership.plans()) {
            assertEquals(FxText.literal(plan.maxLeverage() + "x").getString(),
                    FxShop.lines(plan, POOL, false).getFirst().value().getString(),
                    plan.id() + " writes its own ceiling");
        }
    }

    private static List<String> feeValues(Membership plan) {
        return FxShop.lines(plan, POOL, false).subList(SPREAD, CONVERSION + 1).stream()
                .map(line -> line.value().getString())
                .toList();
    }

    private static String value(List<FxShop.Line> lines, String key) {
        String label = FxText.get(key).getString();
        for (FxShop.Line line : lines) {
            if (line.label().getString().equals(label) && line.value() != null) {
                return line.value().getString();
            }
        }
        throw new AssertionError("no line labelled " + key);
    }

    private static String off(int percent) {
        return FxText.get("fxeasy.shop.fee.off", Integer.toString(percent)).getString();
    }

    private static String standard() {
        return FxText.get("fxeasy.shop.fee.standard").getString();
    }
}
