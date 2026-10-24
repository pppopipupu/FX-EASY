package com.pppopipupu.fxeasy.content;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.entity.AngryFireBall;
import com.pppopipupu.fxeasy.content.entity.AngryMeteor;
import com.pppopipupu.fxeasy.content.entity.AngryStorm;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FxEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FxEasy.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FxEasy.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AngryFireBall>> ANGRY_FIREBALL =
            ENTITY_TYPES.register("angry_fireball", key -> EntityType.Builder
                    .<AngryFireBall>of(AngryFireBall::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<EntityType<?>, EntityType<AngryMeteor>> ANGRY_METEOR =
            ENTITY_TYPES.register("angry_meteor", key -> EntityType.Builder
                    .<AngryMeteor>of(AngryMeteor::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<EntityType<?>, EntityType<AngryStorm>> ANGRY_STORM =
            ENTITY_TYPES.register("angry_storm", key -> EntityType.Builder
                    .<AngryStorm>of(AngryStorm::new, MobCategory.MISC)
                    .sized(4.0F, 4.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ANGRY_PARTICLE =
            PARTICLE_TYPES.register("angry_particle", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ANGRY_PARTICLE_WAND =
            PARTICLE_TYPES.register("angry_particle_wand", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SURPRISE_PARTICLE =
            PARTICLE_TYPES.register("surprise_particle", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ANGRY_THUNDER_PARTICLE =
            PARTICLE_TYPES.register("angry_thunder_particle", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ANGRY_SURGE_PARTICLE =
            PARTICLE_TYPES.register("angry_surge_particle", () -> new SimpleParticleType(false));

    private FxEntities() {
    }
}
