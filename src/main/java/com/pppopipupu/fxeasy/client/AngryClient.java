package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.client.particle.AngryParticle;
import com.pppopipupu.fxeasy.client.particle.AngryParticleGroup;
import com.pppopipupu.fxeasy.client.particle.SurpriseParticle;
import com.pppopipupu.fxeasy.client.particle.ThunderParticle;
import com.pppopipupu.fxeasy.client.render.AngryBlockRenderer;
import com.pppopipupu.fxeasy.client.render.AngryFemaleBlockRenderer;
import com.pppopipupu.fxeasy.client.render.AngryFireBallRenderer;
import com.pppopipupu.fxeasy.client.render.AngryMeteorRenderer;
import com.pppopipupu.fxeasy.client.render.AngryThunderRenderer;
import com.pppopipupu.fxeasy.client.render.AngryStormRenderer;
import com.pppopipupu.fxeasy.client.render.AngryWandRenderer;
import com.pppopipupu.fxeasy.content.AngryClientHooks;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public final class AngryClient {

    public static void install() {
        AngryClientHooks.femaleTick = AngryClientEffects::tickFemale;
        AngryClientHooks.spearEffect = AngryClientEffects::spear;
        AngryClientHooks.wandEffect = AngryClientEffects::wand;
        AngryClientHooks.wandRelease = AngryClientEffects::wandRelease;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        AngryWandRenderer.registerPipelines(event);
        AngryThunderRenderer.registerPipelines(event);
        AngryStormRenderer.registerPipelines(event);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FxBlocks.ANGRY_BLOCK_ENTITY.get(), AngryBlockRenderer::new);
        event.registerBlockEntityRenderer(FxBlocks.ANGRY_FEMALE_ENTITY.get(), AngryFemaleBlockRenderer::new);
        event.registerEntityRenderer(FxEntities.ANGRY_FIREBALL.get(), AngryFireBallRenderer::new);
        event.registerEntityRenderer(FxEntities.ANGRY_METEOR.get(), AngryMeteorRenderer::new);
        event.registerEntityRenderer(FxEntities.ANGRY_STORM.get(), AngryStormRenderer::new);
    }

    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpecial(FxEntities.ANGRY_PARTICLE.get(), new AngryParticle.Provider());
        event.registerSpecial(FxEntities.ANGRY_PARTICLE_WAND.get(), new AngryParticle.WandProvider());
        event.registerSpecial(FxEntities.SURPRISE_PARTICLE.get(), new SurpriseParticle.Provider());
        event.registerSpecial(FxEntities.ANGRY_THUNDER_PARTICLE.get(), new ThunderParticle.Provider());
        event.registerSpecial(FxEntities.ANGRY_SURGE_PARTICLE.get(), new AngryParticle.SurgeProvider());
    }

    public static void registerParticleGroups(RegisterParticleGroupsEvent event) {
        event.register(AngryParticleGroup.TYPE, AngryParticleGroup::new);
    }

    private AngryClient() {
    }
}
