package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.trade.Money;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/// Drawing helpers shared by the panels: text through the bundled font, cards, borders, hit tests and the
/// number formats the terminal speaks. The element counter is reset once per frame by the screen and is what
/// the probe and the end to end runs report. Every helper also audits what it draws against the design area of
/// the frame, so a control that would land outside the screen is reported in the client log instead of being
/// cut off in silence: an element the current clip already cuts is deliberate and left alone.
public final class FxUi {

    public static int elements;

    private static int designWidth;
    private static int designHeight;
    private static final Deque<ScreenRectangle> clips = new ArrayDeque<>();
    private static final Map<String, int[]> overflow = new LinkedHashMap<>();

    private FxUi() {
    }

    /// Starts a frame on a design area of [width] by [height] units, which is the whole screen.
    public static void frame(int width, int height) {
        designWidth = width;
        designHeight = height;
        clips.clear();
    }

    /// Narrows the drawing area the way the graphics scissor does, so the audit can tell an element that is cut
    /// on purpose from one that leaves the screen. Every call pairs with [popClip].
    public static void pushClip(GuiGraphicsExtractor graphics, ScreenRectangle area) {
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        ScreenRectangle parent = clips.peek();
        ScreenRectangle limit = parent == null ? area
                : Objects.requireNonNullElse(area.intersection(parent), ScreenRectangle.empty());
        clips.push(limit);
    }

    public static void popClip(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
        clips.pop();
    }

    /// The overflow seen so far: one entry per primitive and edge, worst overshoot first, or `clean`.
    public static String report() {
        if (overflow.isEmpty()) {
            return "clean";
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, int[]> entry : overflow.entrySet()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            int[] seen = entry.getValue();
            text.append(entry.getKey()).append(" by ").append(seen[0]).append(" (").append(seen[1]).append("x at ")
                    .append(seen[2]).append(',').append(seen[3]).append(" to ").append(seen[4]).append(',').append(seen[5])
                    .append(')');
        }
        return text.toString();
    }

    static void audit(String kind, int left, int top, int right, int bottom) {
        if (designWidth <= 0 || designHeight <= 0) {
            return;
        }
        ScreenRectangle limit = clips.peek();
        if (limit != null && (left < limit.left() || top < limit.top() || right > limit.right() || bottom > limit.bottom())) {
            return;
        }
        int outside = Math.max(Math.max(-left, -top), Math.max(right - designWidth, bottom - designHeight));
        if (outside <= 0) {
            return;
        }
        String edge = -left >= outside ? "left" : -top >= outside ? "top" : right - designWidth >= outside ? "right" : "bottom";
        int[] seen = overflow.computeIfAbsent(kind + " " + edge, key -> new int[6]);
        if (seen[1] == 0) {
            seen[2] = left;
            seen[3] = top;
            seen[4] = right;
            seen[5] = bottom;
        }
        seen[0] = Math.max(seen[0], outside);
        seen[1]++;
    }

    /// Splits [text] into the longest runs of whole words that fit [maxWidth] design units, measuring each
    /// candidate through [width]. A word that does not fit on a line of its own is cut at the last character
    /// that does, so the tail of a body is never dropped.
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && width.applyAsInt(line + " " + word) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (width.applyAsInt(word) > maxWidth) {
                int cut = longestPrefix(word, maxWidth, width);
                if (cut <= 0) {
                    break;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static int longestPrefix(String word, int maxWidth, ToIntFunction<String> width) {
        for (int length = word.length() - 1; length > 0; length--) {
            if (width.applyAsInt(word.substring(0, length)) <= maxWidth) {
                return length;
            }
        }
        return 0;
    }

    public static ScreenRectangle rect(int x, int y, int width, int height) {
        return new ScreenRectangle(x, y, width, height);
    }

    /// [area] grown by [pad] on every side and kept inside the design area of the frame: an outline drawn around
    /// a cell that touches the edge of the screen would otherwise leave it.
    public static ScreenRectangle grow(ScreenRectangle area, int pad) {
        int left = Math.max(0, area.left() - pad);
        int top = Math.max(0, area.top() - pad);
        int right = designWidth <= 0 ? area.right() + pad : Math.min(designWidth, area.right() + pad);
        int bottom = designHeight <= 0 ? area.bottom() + pad : Math.min(designHeight, area.bottom() + pad);
        return rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, int x, int y, Component text, int colour) {
        graphics.text(font, text, x, y, colour, false);
        audit("text", x, y, x + font.width(text), y + font.lineHeight);
        elements++;
    }

    public static void right(GuiGraphicsExtractor graphics, Font font, int right, int y, Component text, int colour) {
        text(graphics, font, right - font.width(text), y, text, colour);
    }

    public static int labeledRight(GuiGraphicsExtractor graphics, Font font, int right, int y, Component label, Component value,
            int colour) {
        int cursor = right - font.width(value);
        text(graphics, font, cursor, y, value, colour);
        cursor -= font.width(label) + 3;
        text(graphics, font, cursor, y, label, FxTheme.MUTED);
        return cursor - 5;
    }

    public static void center(GuiGraphicsExtractor graphics, Font font, int centerX, int y, Component text, int colour) {
        text(graphics, font, centerX - font.width(text) / 2, y, text, colour);
    }

    /// Draws an item stack [scale] times the vanilla 16x16 GUI size. Scaling the pose instead of the sprite
    /// keeps the atlas resolution and lines the icon up with the design unit grid, so [x] and [y] should be
    /// multiples of [scale].
    public static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int scale) {
        if (stack.isEmpty() || scale < 1) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.item(stack, x / scale, y / scale);
        graphics.pose().popMatrix();
        audit("item", x, y, x + 16 * scale, y + 16 * scale);
        elements++;
    }

    record Crop(float u0, float u1, float v0, float v1) {
    }

    /// The crop of an illustration that fills a rectangle without stretching it: the file is scaled up until it
    /// covers the rectangle on both axes, what is left over is cut on each axis, and [anchor] decides where on
    /// the file the horizontal band that survives starts - 0 keeps the top of the file, 0.5 its middle and 1 its
    /// bottom, which is what keeps the head of a subject when a tile of a character is cropped into a card.
    /// [artWidth] and [artHeight] are the pixel size of the file.
    static Crop cover(int areaWidth, int areaHeight, int artWidth, int artHeight, double anchor) {
        if (areaWidth <= 0 || areaHeight <= 0 || artWidth <= 0 || artHeight <= 0) {
            return new Crop(0.0F, 1.0F, 0.0F, 1.0F);
        }
        double scale = Math.max(areaWidth / (double) artWidth, areaHeight / (double) artHeight);
        double sourceWidth = Math.min(artWidth, areaWidth / scale);
        double sourceHeight = Math.min(artHeight, areaHeight / scale);
        double offsetX = (artWidth - sourceWidth) * 0.5D;
        double offsetY = (artHeight - sourceHeight) * Math.clamp(anchor, 0.0D, 1.0D);
        return new Crop((float) (offsetX / artWidth), (float) ((offsetX + sourceWidth) / artWidth),
                (float) (offsetY / artHeight), (float) ((offsetY + sourceHeight) / artHeight));
    }

    public static void clipped(GuiGraphicsExtractor graphics, Font font, ScreenRectangle clip, int x, int y, Component text, int colour) {
        if (x >= clip.right()) {
            return;
        }
        int width = font.width(text);
        int limit = Math.max(0, clip.right() - x);
        if (width <= limit) {
            text(graphics, font, x, y, text, colour);
            return;
        }
        pushClip(graphics, clip);
        text(graphics, font, x, y, text, colour);
        popClip(graphics);
    }

    public static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        graphics.fill(x0, y0, x1, y1, colour);
        audit("fill", x0, y0, x1, y1);
        elements++;
    }

    public static void gradient(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom) {
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        graphics.fillGradient(x0, y0, x1, y1, top, bottom);
        audit("gradient", x0, y0, x1, y1);
        elements++;
    }

    public static void card(GuiGraphicsExtractor graphics, ScreenRectangle area) {
        fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.CARD);
        border(graphics, area, FxTheme.CARD_BORDER);
    }

    public static void border(GuiGraphicsExtractor graphics, ScreenRectangle area, int colour) {
        graphics.outline(area.left(), area.top(), area.width(), area.height(), colour);
        audit("border", area.left(), area.top(), area.right(), area.bottom());
        elements += 4;
    }

    public static void separator(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
        fill(graphics, x0, y, x1, y + 1, FxTheme.CARD_BORDER);
    }

    public static void dashed(GuiGraphicsExtractor graphics, int x0, int x1, int y, int colour, int dash) {
        for (int x = x0; x < x1; x += dash * 2) {
            fill(graphics, x, y, Math.min(x + dash, x1), y + 1, colour);
        }
    }

    public static boolean hit(ScreenRectangle area, double x, double y) {
        return x >= area.left() && x < area.right() && y >= area.top() && y < area.bottom();
    }

    public static String price(double price) {
        double magnitude = Math.abs(price);
        if (magnitude >= 1_000.0D) {
            return String.format(Locale.ROOT, "%,.1f", price);
        }
        if (magnitude >= 1.0D) {
            return String.format(Locale.ROOT, "%.4f", price);
        }
        return String.format(Locale.ROOT, "%.7f", price);
    }

    public static String percent(double percent) {
        return String.format(Locale.ROOT, "%+.2f%%", percent);
    }

    /// A small fraction as a percentage with enough decimals to stay non zero, for spreads and swap rates.
    public static String rate(double fraction) {
        return String.format(Locale.ROOT, "%.4f%%", fraction * 100.0D);
    }

    /// Same as [rate] with the sign kept, for a swap rate that is a credit on one side of a pair and a cost on
    /// the other.
    public static String signedRate(double fraction) {
        return String.format(Locale.ROOT, "%+.4f%%", fraction * 100.0D);
    }

    public static String factor(double factor) {
        return String.format(Locale.ROOT, "%.2fx", factor);
    }

    public static String level(double level) {
        return Double.isFinite(level) ? String.format(Locale.ROOT, "%.1f%%", level * 100.0D) : "-";
    }

    /// An item count with two decimals, for the aggregates the terminal quotes in items of one currency.
    public static String amount(double items) {
        return String.format(Locale.ROOT, "%,.2f", items);
    }

    /// The size of an order as the ticket quotes it: whole lots, one lot being one item of the base currency of
    /// its pair, which is the smallest size the platform accepts and the step the slider moves in.
    public static String lots(int units) {
        return String.format(Locale.ROOT, "%,d", units);
    }

    /// Same with the sign kept, for a profit or a debt that can point either way.
    public static String signedAmount(double items) {
        return String.format(Locale.ROOT, "%+,.2f", items);
    }

    /// Game days as the credit record reads them, one decimal: a debt ages visibly within a session.
    public static String days(double days) {
        return String.format(Locale.ROOT, "%.1f", days);
    }

    /// Market ticks in one minute, which is the clock the market counts in: the service advances it once per
    /// server tick, so a market minute is a minute of play.
    public static final long TICKS_PER_MINUTE = 1_200L;

    /// A market time as a player reads it: minutes below an hour, hours below a day, days beyond that. The market
    /// clock is a duration measured from the genesis of the save, not a wall clock the world does not have, so it
    /// is quoted the way a session is.
    public static String marketTime(long ticks) {
        long minutes = ticks / TICKS_PER_MINUTE;
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return minutes % 60L == 0L ? hours + "h" : hours + "h " + minutes % 60L + "m";
        }
        return hours % 24L == 0L ? hours / 24L + "d" : hours / 24L + "d " + hours % 24L + "h";
    }

    /// An amount of money as the item count it is: six decimals, trimmed of trailing zeros, and trimmed of the
    /// decimal point as well when nothing but whole items are left, so a balance of 64 items reads `64` and not
    /// `64.000000` or `0.0`.
    public static String units(long micro) {
        String formatted = String.format(Locale.ROOT, "%,.6f", Money.units(micro));
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return end <= 0 ? "0" : formatted.substring(0, end);
    }

    public static Component money(long micro, String code) {
        return FxText.literal("%s %s", units(micro), code);
    }
}
