package com.edgewave.item;

import com.edgewave.Edgewave;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item EDGE_SWORD = Registry.register(Registries.ITEM, Edgewave.id("edge_sword"),
            new SlashSword(ToolMaterials.NETHERITE, new Item.Settings().rarity(Rarity.EPIC).fireproof()
                    .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.4f))));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(EDGE_SWORD));
    }
}
