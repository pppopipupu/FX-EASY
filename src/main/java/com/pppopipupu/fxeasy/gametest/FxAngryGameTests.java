package com.pppopipupu.fxeasy.gametest;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.AngryHealth;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxEntities;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.AngryFemaleBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.entity.AngryFireBall;
import com.pppopipupu.fxeasy.content.entity.AngryMeteor;
import com.pppopipupu.fxeasy.content.block.entity.AngryBlockEntity;
import com.pppopipupu.fxeasy.content.entity.AngryStorm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FxAngryGameTests {

    private static final int MAX_TICKS = 400;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, FxEasy.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PLACEMENT =
            FUNCTIONS.register("angry_block_places_and_breaks", () -> FxAngryGameTests::placement);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LIGHTNING =
            FUNCTIONS.register("angry_lightning_damages", () -> FxAngryGameTests::lightning);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SWORD =
            FUNCTIONS.register("angry_sword_loves_female", () -> FxAngryGameTests::sword);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HEALTH =
            FUNCTIONS.register("angry_health_absorbs", () -> FxAngryGameTests::health);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> METEOR =
            FUNCTIONS.register("angry_meteor_impacts", () -> FxAngryGameTests::meteor);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> UNSTABLE_COLLAPSE =
            FUNCTIONS.register("angry_unstable_block_collapses", () -> FxAngryGameTests::unstableCollapse);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STORM =
            FUNCTIONS.register("angry_storm_phases_and_immunity", () -> FxAngryGameTests::storm);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> VOID_ANNIHILATION =
            FUNCTIONS.register("angry_storm_void_annihilates", () -> FxAngryGameTests::stormVoidAnnihilates);

    private FxAngryGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FxAngryGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, environment, "angry_block_places_and_breaks", PLACEMENT);
        register(event, environment, "angry_lightning_damages", LIGHTNING);
        register(event, environment, "angry_sword_loves_female", SWORD);
        register(event, environment, "angry_health_absorbs", HEALTH);
        register(event, environment, "angry_meteor_impacts", METEOR);
        register(event, environment, "angry_unstable_block_collapses", UNSTABLE_COLLAPSE);
        register(event, environment, "angry_storm_phases_and_immunity", STORM);
        register(event, environment, "angry_storm_void_annihilates", VOID_ANNIHILATION);
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            String name, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, name),
                new FunctionGameTestInstance(function.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
    }

    private static void placement(GameTestHelper helper) {
        BlockPos core = new BlockPos(1, 2, 1);
        BlockState state = FxBlocks.ANGRY_BLOCK.get().defaultBlockState()
                .setValue(MultiPartBlock.FACING, Direction.NORTH);
        helper.setBlock(core, state);
        assertParts(helper, core, FxBlocks.ANGRY_BLOCK.get().defaultBlockState());

        helper.getLevel().destroyBlock(helper.absolutePos(core), false);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos part = core.offset(x, y, z);
                    helper.assertTrue(helper.getBlockState(part).isAir(),
                            "a part survived the removal of its core at " + part);
                }
            }
        }

        BlockPos female = new BlockPos(5, 2, 1);
        helper.setBlock(female, FxBlocks.ANGRY_FEMALE_BLOCK.get().defaultBlockState()
                .setValue(MultiPartBlock.FACING, Direction.NORTH));
        assertParts(helper, female, FxBlocks.ANGRY_FEMALE_BLOCK.get().defaultBlockState());

        helper.getLevel().destroyBlock(helper.absolutePos(female.offset(1, 0, 0)), false);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    helper.assertTrue(helper.getBlockState(female.offset(x, y, z)).isAir(),
                            "breaking a part left the structure behind");
                }
            }
        }
        helper.succeed();
    }

    private static void assertParts(GameTestHelper helper, BlockPos core, BlockState expected) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockState state = helper.getBlockState(core.offset(x, y, z));
                    helper.assertTrue(state.is(expected.getBlock()), "a part of the structure is missing");
                    helper.assertTrue(state.getValue(MultiPartBlock.IS_CORE) == (x == 0 && y == 0 && z == 0),
                            "a part carries the wrong core flag");
                }
            }
        }
    }

    private static void lightning(GameTestHelper helper) {
        BlockPos core = new BlockPos(1, 2, 1);
        helper.setBlock(core, FxBlocks.ANGRY_BLOCK.get().defaultBlockState()
                .setValue(AngryBlock.IS_LIGHTNING, true));
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, core.offset(1, 0, 0));
        helper.succeedWhen(() -> helper.assertTrue(piglin.getHealth() < piglin.getMaxHealth(),
                "the lightning core did not damage a nearby living entity"));
    }

    private static void sword(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        BlockPos female = new BlockPos(3, 2, 1);
        helper.setBlock(female, FxBlocks.ANGRY_FEMALE_BLOCK.get().defaultBlockState()
                .setValue(MultiPartBlock.FACING, Direction.NORTH));
        ItemStack stack = new ItemStack(FxItems.ANGRY_SWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.succeedWhen(() -> {
            if (helper.getLevel().getGameTime() % 20 == 0) {
                stack.getItem().onUseTick(helper.getLevel(), player, stack, 0);
            }
            helper.assertTrue(helper.getBlockState(female).getValue(AngryFemaleBlock.IS_LOVE),
                    "the sword did not mark the female core as loved");
        });
    }

    private static void health(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        AngryHealth.set(player, 5.0F);
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), 3.0F);
        helper.assertTrue(Math.abs(AngryHealth.get(player) - 2.0F) < 1.0E-4F,
                "the Angry health did not absorb the damage");
        helper.assertTrue(Math.abs(player.getHealth() - player.getMaxHealth()) < 1.0E-4F,
                "the player's own health was damaged before the Angry health ran out");
        AngryHealth.releaseHalf(player);
        helper.assertTrue(Math.abs(AngryHealth.get(player) - 1.0F) < 1.0E-4F,
                "releasing the wand did not keep half of the Angry health");
        helper.succeed();
    }

    private static void meteor(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 2, 1);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                helper.setBlock(target.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        AngryMeteor meteor = helper.spawn(FxEntities.ANGRY_METEOR.get(),
                new Vec3(target.getX() + 0.5, target.getY() + 2.0, target.getZ() + 0.5));
        meteor.setDeltaMovement(0.0, -1.2, 0.0);
        helper.assertTrue(!meteor.isRemoved(), "the meteor was removed before it ticked");
        for (int tick = 0; tick < 260 && !meteor.isRemoved(); tick++) {
            meteor.tick();
        }
        helper.assertTrue(meteor.isRemoved(), "the meteor did not impact");
        BlockPos absolute = helper.absolutePos(target);
        boolean found = BlockPos.betweenClosedStream(absolute.offset(-12, -24, -12),
                        absolute.offset(12, 24, 12))
                .anyMatch(pos -> helper.getLevel().getBlockState(pos).is(FxBlocks.ANGRY_BLOCK.get()));
        helper.assertTrue(found, "the meteor left no Angry blocks");
        helper.succeed();
    }

    private static void unstableCollapse(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 2);
        for (int x = -3; x <= 4; x++) {
            for (int z = -3; z <= 4; z++) {
                helper.setBlock(core.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z < 2; z++) {
                    helper.setBlock(core.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos absCore = helper.absolutePos(core);
        boolean placed = AngryStorm.placeUnstableCore(
                helper.getLevel(), absCore, helper.getLevel().getRandom(), 200, 1000);
        helper.assertTrue(placed, "failed to place unstable AngryBlock core");

        BlockState coreState = helper.getBlockState(core);
        helper.assertTrue(coreState.is(FxBlocks.ANGRY_BLOCK.get()), "core is not AngryBlock");
        helper.assertTrue(coreState.getValue(AngryBlock.IS_UNSTABLE), "core is not marked IS_UNSTABLE");

        AngryBlockEntity blockEntity = helper.getBlockEntity(core, AngryBlockEntity.class);
        helper.assertTrue(blockEntity.getCollapseLimit() >= 200 && blockEntity.getCollapseLimit() <= 1000,
                "collapse limit out of range [200, 1000]: " + blockEntity.getCollapseLimit());

        blockEntity.setCollapseLimit(5);
        for (int i = 0; i < 6; i++) {
            BlockState current = helper.getLevel().getBlockState(absCore);
            if (current.is(FxBlocks.ANGRY_BLOCK.get())) {
                AngryBlockEntity.tick(helper.getLevel(), absCore, current, blockEntity);
            }
        }

        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos part = core.offset(x, y, z);
                    helper.assertTrue(!helper.getBlockState(part).is(FxBlocks.ANGRY_BLOCK.get()),
                            "unstable part survived collapse at " + part);
                }
            }
        }
        helper.succeed();
    }

    private static void storm(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        BlockPos highCenter = center.above(100);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                helper.setBlock(highCenter.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        Player caster = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absHighCenter = helper.absolutePos(highCenter);
        caster.setPos(absHighCenter.getX() + 0.5, absHighCenter.getY(), absHighCenter.getZ() + 0.5);
        caster.setHealth(caster.getMaxHealth());
        AngryHealth.set(caster, 20.0F);

        AngryStorm storm = new AngryStorm(
                helper.getLevel(),
                absHighCenter.getX() + 0.5,
                absHighCenter.getY() + 0.2,
                absHighCenter.getZ() + 0.5,
                caster,
                1.75F);
        helper.getLevel().addFreshEntity(storm);

        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, highCenter.offset(2, 0, 2));
        helper.assertTrue(storm.isImmune(caster), "caster should be immune to own storm");
        helper.assertTrue(!storm.isImmune(piglin), "piglin should not be immune to storm");
        helper.assertTrue(storm.phase() == 1, "initial storm phase should be 1");

        boolean[] phasesSeen = new boolean[8];
        for (int tick = 0; tick < AngryStorm.TOTAL_DURATION && !storm.isRemoved(); tick++) {
            piglin.invulnerableTime = 0;
            storm.tick();
            phasesSeen[storm.phase()] = true;
        }

        helper.assertTrue(Math.abs(caster.getHealth() - caster.getMaxHealth()) < 1.0E-4F,
                "caster took damage from their own storm");
        helper.assertTrue(piglin.getHealth() < piglin.getMaxHealth() || piglin.isDeadOrDying() || piglin.isRemoved(),
                "piglin did not take damage from the storm");
        helper.assertTrue(storm.isRemoved(), "storm was not removed after 900 ticks");
        for (int phase = 1; phase <= 7; phase++) {
            helper.assertTrue(phasesSeen[phase], "the storm never reached phase " + phase);
        }

        boolean foundUnstable = BlockPos.betweenClosedStream(absHighCenter.offset(-70, -20, -70),
                        absHighCenter.offset(70, 20, 70))
                .anyMatch(pos -> {
                    BlockState state = helper.getLevel().getBlockState(pos);
                    return state.is(FxBlocks.ANGRY_BLOCK.get()) && state.getValue(AngryBlock.IS_UNSTABLE);
                });
        helper.assertTrue(foundUnstable, "storm did not leave any unstable Angry blocks");
        discardFireBalls(helper, absHighCenter);
        helper.succeed();
    }

    private static void discardFireBalls(GameTestHelper helper, BlockPos absCenter) {
        AABB area = new AABB(absCenter).inflate(AngryStorm.BASE_RADIUS * 3.0);
        for (AngryFireBall fireBall : helper.getLevel().getEntitiesOfClass(AngryFireBall.class, area)) {
            fireBall.discard();
        }
    }

    private static void stormVoidAnnihilates(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        BlockPos highCenter = center.above(100);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                helper.setBlock(highCenter.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        BlockPos absHighCenter = helper.absolutePos(highCenter);
        AngryStorm storm = new AngryStorm(helper.getLevel(),
                absHighCenter.getX() + 0.5, absHighCenter.getY() + 0.2, absHighCenter.getZ() + 0.5, null, 1.0F);
        helper.getLevel().addFreshEntity(storm);
        for (int tick = 0; tick < AngryStorm.PHASE_5_END; tick++) {
            storm.tick();
        }
        helper.assertTrue(storm.phase() == 6, "the storm did not enter the void phase");
        for (int x = -1; x <= 3; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = -2; z <= 2; z++) {
                    helper.setBlock(highCenter.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, highCenter.offset(3, 0, 0));
        for (int tick = 0; tick < 5; tick++) {
            storm.tick();
        }
        helper.assertTrue(piglin.getDeltaMovement().y > 0.02, "the void did not pull the piglin upward");
        for (int tick = 0; tick < 80 && !piglin.isRemoved(); tick++) {
            storm.tick();
            piglin.tick();
        }
        helper.assertTrue(piglin.isRemoved(), "the void did not annihilate the piglin inside its sphere");
        discardFireBalls(helper, absHighCenter);
        helper.succeed();
    }
}
