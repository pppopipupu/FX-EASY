package com.pppopipupu.fxeasy.client;

import module java.base;

import net.minecraft.client.Minecraft;

public final class FxPhoneTerminal {

    private FxPhoneTerminal() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FxPhoneScreen());
    }
}
