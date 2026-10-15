package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.panel.FxControls;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/// The lounge the top tier of a subscription opens, which the shop page opens from its own bar: what the platform
/// has set aside for the account, one gift a period while the plan runs, each of them drawn as the stack it will
/// be handed over as with the button that takes it. The list scrolls with the wheel, because a lounge that was
/// left alone can hold more than a card shows, and every row carries the position its claim addresses, which is
/// the position the account snapshot listed it at.
///
/// The overlay owns the pointer and the keyboard while it is up, like every other overlay of the terminal: a
/// click can never reach a control of the panel behind it. It is the player's to close, and closing it claims
/// nothing.
public final class FxLounge {

    private static final int MAX_ROWS = 8;
    private static final int CARD_WIDTH = 250;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int ROW = 18;
    private static final int ICON = 16;
    private static final int CLAIM_WIDTH = 46;
    private static final int HEADER = 2 * LINE + 4;
    private static final int FOOTER = LINE + 6;
    private static final int CLOSE_WIDTH = 10;
    private static final int SCROLL_STEP = 2;
    private static final int COVER = ARGB.color(200, 4, 6, 9);

    private final FxInput input = new FxInput();
    private boolean visible;
    private int scroll;
    private boolean logged;

    public boolean active() {
        return this.visible;
    }

    /// The pointer state of the overlay, which the screen fills from the mouse callbacks while it is up.
    public FxInput input() {
        return this.input;
    }

    public void open() {
        this.visible = true;
        this.scroll = 0;
    }

    /// Puts the lounge away, which is what the close cell, a click beside the list and Escape do. Nothing is
    /// claimed by closing it.
    public void dismiss() {
        this.visible = false;
        this.input.clear();
        this.logged = false;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        ScreenRectangle close = closeArea(designWidth, designHeight);
        ScreenRectangle list = listArea(designWidth, designHeight);
        List<ItemStack> gifts = state.lounge();
        int visible = rows(designWidth, designHeight);
        int maximum = Math.max(0, gifts.size() - visible);
        if (this.input.scroll != 0.0D && this.input.hovered(list)) {
            this.scroll -= (int) Math.signum(this.input.scroll) * SCROLL_STEP;
            this.input.scroll = 0.0D;
        }
        this.scroll = Math.clamp(this.scroll, 0, maximum);
        if (!this.logged) {
            this.logged = true;
            FxEasy.LOGGER.info("FX lounge shown, {} gifts, {} rows, card {}x{} at {},{}", gifts.size(), visible,
                    card.width(), card.height(), card.left(), card.top());
        }
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.9F));
        FxUi.text(graphics, font, card.left() + PAD, card.top() + PAD, FxText.get("fxeasy.lounge.title"),
                FxTheme.ACCENT);
        FxControls.button(graphics, font, close, FxText.get("fxeasy.lounge.close"), FxTheme.BUTTON, FxTheme.TEXT,
                this.input.hovered(close), this.input.pressed && this.input.hovered(close), true);
        Component next = state.loungeOpen()
                ? FxText.get("fxeasy.lounge.next", FxUi.marketTime(state.loungeTicksLeft()))
                : FxText.get("fxeasy.lounge.lapsed");
        FxUi.text(graphics, font, card.left() + PAD, card.top() + PAD + LINE + 1, next, FxTheme.MUTED);
        if (gifts.isEmpty()) {
            FxUi.text(graphics, font, list.left(), list.top() + 2, FxText.get("fxeasy.lounge.empty"), FxTheme.MUTED);
        }
        FxUi.pushClip(graphics, list);
        for (int index = this.scroll; index < Math.min(gifts.size(), this.scroll + visible); index++) {
            ScreenRectangle claim = claimArea(index, gifts.size(), designWidth, designHeight);
            ScreenRectangle row = FxUi.rect(list.left(), list.top() + (index - this.scroll) * ROW, list.width(), ROW);
            FxUi.item(graphics, gifts.get(index), row.left(), row.top() + 1, 1);
            FxUi.clipped(graphics, font, row, row.left() + ICON + FxTheme.GAP, row.top() + (ROW - 8) / 2,
                    gifts.get(index).getHoverName(), FxTheme.TEXT);
            FxControls.button(graphics, font, claim, FxText.get("fxeasy.lounge.claim"), FxTheme.BUTTON, FxTheme.ACCENT,
                    this.input.hovered(claim), this.input.pressed && this.input.hovered(claim), true);
            if (this.input.clicked(claim)) {
                FxActions.claimGift(index);
                FxEasy.LOGGER.info("FX terminal lounge claim {} of {}", index, gifts.size());
                return;
            }
        }
        FxUi.popClip(graphics);
        FxUi.text(graphics, font, card.left() + PAD, card.bottom() - FOOTER + 2, FxText.get("fxeasy.lounge.note"),
                FxTheme.MUTED);
        if (this.input.clicked(close) || this.input.clicked(card)) {
            dismiss();
        }
    }

    public static ScreenRectangle card(int designWidth, int designHeight) {
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int wanted = HEADER + ROW * MAX_ROWS + FOOTER;
        int height = Math.min(wanted, designHeight - FxTheme.PAD * 2);
        return FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
    }

    public static ScreenRectangle closeArea(int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        return FxUi.rect(card.right() - PAD - CLOSE_WIDTH, card.top() + PAD - 1, CLOSE_WIDTH, LINE + 1);
    }

    public static ScreenRectangle listArea(int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        return FxUi.rect(card.left() + PAD, card.top() + HEADER, Math.max(1, card.width() - PAD * 2),
                Math.max(ROW, card.height() - HEADER - FOOTER));
    }

    public static int rows(int designWidth, int designHeight) {
        return Math.max(1, listArea(designWidth, designHeight).height() / ROW);
    }

    /// The cell that claims one gift, or null while the list is not showing it: the list scrolls and a gift that
    /// does not exist is no gift at all, so a gift under the window of the list - or one an index past the end
    /// names - is no cell of this frame, and a scripted click at it reports that it found none.
    public ScreenRectangle claimArea(int index, int gifts, int designWidth, int designHeight) {
        if (index < 0 || index >= gifts) {
            return null;
        }
        int visible = rows(designWidth, designHeight);
        int top = Math.clamp(this.scroll, 0, Math.max(0, gifts - visible));
        if (index < top || index >= top + visible) {
            return null;
        }
        ScreenRectangle list = listArea(designWidth, designHeight);
        return FxUi.rect(list.right() - CLAIM_WIDTH, list.top() + (index - top) * ROW, CLAIM_WIDTH, ROW - 2);
    }
}
