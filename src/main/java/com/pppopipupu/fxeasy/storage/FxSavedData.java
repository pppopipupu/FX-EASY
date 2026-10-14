package com.pppopipupu.fxeasy.storage;

import module java.base;

import com.mojang.serialization.Codec;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/// Server wide persistence of the FX economy, held by `MinecraftServer.getDataStorage()` so it covers every
/// dimension and every player.
public final class FxSavedData extends SavedData {

    private static final Codec<FxSavedData> CODEC = FxSnapshot.CODEC.xmap(FxSavedData::new, FxSavedData::snapshot);

    public static final SavedDataType<FxSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FxEasy.MODID, "market"),
            FxSavedData::new,
            CODEC);

    private FxSnapshot snapshot;

    private FxSavedData() {
        this(FxSnapshot.EMPTY);
    }

    public FxSavedData(FxSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public FxSnapshot snapshot() {
        return this.snapshot;
    }

    public void update(FxSnapshot snapshot) {
        this.snapshot = snapshot;
        setDirty();
    }
}
