package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

/// A field is the value its owner submits, so what was typed has to outlive the focus: a click continues the
/// text when the owner seeds the field with it, and the box keeps showing it while the owner has no value behind
/// it to fall back to. The registration of the account opening is such an owner - seeding it with an empty
/// string is what wiped the name on the second click.
class FxFieldTest {

    private static final ScreenRectangle AREA = FxUi.rect(10, 10, 100, 12);

    @Test
    void theTextOutlivesTheFocusAndASecondClick() {
        FxField field = FxField.ascii(12);
        FxInput input = new FxInput();
        click(field, input);
        assertTrue(field.focused(input), "a click on the box takes the focus");
        field.type("Steve");
        assertEquals("Steve", field.shown(input, ""), "a focused box shows what was typed");
        input.focus = null;
        assertEquals("Steve", field.text(), "the text is what the owner submits, focused or not");
        assertEquals("Steve", field.shown(input, field.text()), "an unfocused box keeps showing the text");
        click(field, input);
        assertEquals("Steve", field.text(), "a second click continues the text");
        field.type("!");
        assertEquals("Steve!", field.text());
    }

    @Test
    void aDigitsFieldKeepsItsCapAndItsFilter() {
        FxField field = FxField.digits(3);
        FxInput input = new FxInput();
        click(field, input, "100");
        assertEquals("100", field.text(), "the click seeds the box with the number it is editing");
        field.type("7x");
        assertEquals("100", field.text(), "a digits field refuses everything that is not a digit and holds three");
        assertTrue(field.backspace());
        field.type("x5");
        assertEquals("105", field.text());
        field.backspace();
        field.backspace();
        field.backspace();
        assertEquals("", field.text());
        assertTrue(!field.backspace(), "backspacing an empty field falls through to the screen");
    }

    @Test
    void aNameFieldTakesPrintableAsciiOnly() {
        FxField field = FxField.ascii(16);
        field.type("Kurumi \u00d7 name\t!");
        assertEquals("Kurumi  name!", field.text(), "the bundled font can only draw printable ASCII");
    }

    private static void click(FxField field, FxInput input) {
        click(field, input, field.text());
    }

    private static void click(FxField field, FxInput input, String seed) {
        input.pressed = true;
        input.mouseX = AREA.left() + 1;
        input.mouseY = AREA.top() + 1;
        field.click(input, AREA, seed);
        input.clear();
    }
}
