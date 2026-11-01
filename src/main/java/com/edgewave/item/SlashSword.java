package com.edgewave.item;

import com.edgewave.combat.Slash;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** A sword whose right click throws a combo of cutting arcs and flying cutting waves; hold to keep slashing. */
public class SlashSword extends SwordItem {
    public SlashSword(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) Slash.fire(sp);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) Slash.fire(sp);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }
}
