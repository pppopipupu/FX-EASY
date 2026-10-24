package com.pppopipupu.fxeasy.content;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.item.AngryBlockItem;
import com.pppopipupu.fxeasy.content.item.AngryGunItem;
import com.pppopipupu.fxeasy.content.item.AngryMeteorSummoner;
import com.pppopipupu.fxeasy.content.item.AngrySpearItem;
import com.pppopipupu.fxeasy.content.item.AngryStormGenerator;
import com.pppopipupu.fxeasy.content.item.AngrySwordItem;
import com.pppopipupu.fxeasy.content.item.AngryWand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FxItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FxEasy.MODID);

    public static final DeferredItem<FxPhoneItem> FX_PHONE =
            ITEMS.registerItem("fx_phone", FxPhoneItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<AngryBlockItem> ANGRY_BLOCK_ITEM = ITEMS.registerItem("angry_block",
            properties -> new AngryBlockItem(FxBlocks.ANGRY_BLOCK.get(), properties, 0),
            properties -> properties.overrideDescription("block.fxeasy.angry_block"));
    public static final DeferredItem<AngryBlockItem> ANGRY_LIGHTNING_BLOCK_ITEM = ITEMS.registerItem(
            "angry_lightning_block",
            properties -> new AngryBlockItem(FxBlocks.ANGRY_BLOCK.get(), properties, 1),
            properties -> properties.overrideDescription("block.fxeasy.angry_lightning_block"));
    public static final DeferredItem<AngryBlockItem> ANGRY_ATOMIC_BLOCK_ITEM = ITEMS.registerItem("angry_atomic_block",
            properties -> new AngryBlockItem(FxBlocks.ANGRY_BLOCK.get(), properties, 2),
            properties -> properties.overrideDescription("block.fxeasy.angry_atomic_block"));
    public static final DeferredItem<BlockItem> ANGRY_FEMALE_ITEM = ITEMS.registerItem("angry_female_block",
            properties -> new BlockItem(FxBlocks.ANGRY_FEMALE_BLOCK.get(), properties),
            properties -> properties.overrideDescription("block.fxeasy.angry_female_block"));

    public static final DeferredItem<AngrySwordItem> ANGRY_SWORD = ITEMS.registerItem("angry_sword",
            AngrySwordItem::new, properties -> properties.sword(AngryMaterials.ANGRY, 0.0F, 0.0F));
    public static final DeferredItem<AngrySpearItem> ANGRY_SPEAR = ITEMS.registerItem("angry_spear",
            AngrySpearItem::new, properties -> properties.sword(AngryMaterials.SURPRISE, 0.0F, 0.0F));
    public static final DeferredItem<AngryGunItem> ANGRY_GUN = ITEMS.registerItem("angry_gun",
            AngryGunItem::new, FxItems::surpriseAttributes);
    public static final DeferredItem<AngryWand> ANGRY_WAND = ITEMS.registerItem("angry_wand",
            AngryWand::new, FxItems::surpriseAttributes);
    public static final DeferredItem<AngryMeteorSummoner> ANGRY_METEOR_SUMMONER =
            ITEMS.registerItem("angry_meteor_summoner", AngryMeteorSummoner::new,
                    properties -> properties.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<AngryStormGenerator> ANGRY_STORM_GENERATOR =
            ITEMS.registerItem("angry_storm_generator", AngryStormGenerator::new,
                    properties -> properties.stacksTo(1).rarity(Rarity.EPIC));

    private static Item.Properties surpriseAttributes(Item.Properties properties) {
        return properties.attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, -10.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 0.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build());
    }

    private FxItems() {
    }
}
