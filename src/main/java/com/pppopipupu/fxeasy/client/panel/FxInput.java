package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import net.minecraft.client.gui.navigation.ScreenRectangle;

/// Per frame interaction state. The screen fills it from the mouse and keyboard callbacks, the panels read the
/// mouse position and claim a drag id, so a click can never be handled by two controls in one frame. Text focus
/// is shared the same way: a field claims it on click and the screen drops it when a frame clicked nothing.
public final class FxInput {

    public int mouseX;
    public int mouseY;
    public boolean down;
    public boolean pressed;
    public boolean released;
    public double scroll;
    public int dragId = -1;
    public Object focus;
    public boolean focusClaimed;

    public boolean hovered(ScreenRectangle area) {
        return FxUi.hit(area, this.mouseX, this.mouseY);
    }

    public boolean clicked(ScreenRectangle area) {
        return this.pressed && hovered(area);
    }

    public boolean dragging(int id) {
        return this.down && this.dragId == id;
    }

    public void beginDrag(int id) {
        this.dragId = id;
    }

    public void endDrag() {
        this.dragId = -1;
    }

    public void clear() {
        this.pressed = false;
        this.released = false;
        this.scroll = 0.0D;
        this.focusClaimed = false;
    }
}
