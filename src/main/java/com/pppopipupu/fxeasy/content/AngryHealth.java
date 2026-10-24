package com.pppopipupu.fxeasy.content;

import module java.base;

import com.mojang.serialization.Codec;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/// A player's Angry health: a pool granted by the wand that damage consumes before the player's own health. It is
/// kept in a synced data attachment, so the client draws the same number the server charges, and it is serialized
/// with the player rather than copied on death.
public final class AngryHealth {

    public static final float MAX = 20.0F;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FxEasy.MODID);

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> ANGRY_HEALTH =
            ATTACHMENT_TYPES.register("angry_health", () -> AttachmentType.builder(() -> 0.0F)
                    .serialize(Codec.FLOAT.fieldOf("value"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    public static AttachmentType<Float> type() {
        return ANGRY_HEALTH.get();
    }

    public static float get(Player player) {
        return player.getData(type());
    }

    public static void set(Player player, float value) {
        float clamped = Math.clamp(value, 0.0F, MAX);
        if (Math.abs(clamped - get(player)) > 1.0E-4F) {
            player.setData(type(), clamped);
        }
    }

    public static float grant(Player player, float amount) {
        float before = get(player);
        set(player, before + amount);
        return get(player) - before;
    }

    public static float releaseHalf(Player player) {
        float before = get(player);
        set(player, before * 0.5F);
        return before;
    }

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float pool = get(player);
        float damage = event.getNewDamage();
        if (pool <= 0.0F || damage <= 0.0F) {
            return;
        }
        float absorbed = Math.min(pool, damage);
        set(player, pool - absorbed);
        event.setNewDamage(damage - absorbed);
    }

    private AngryHealth() {
    }
}
