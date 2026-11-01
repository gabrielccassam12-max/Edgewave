package com.edgewave;

import com.edgewave.item.ModItems;
import com.edgewave.particle.ModParticles;
import com.edgewave.util.Effects;
import com.edgewave.combat.Skills;
import com.edgewave.net.CdPayload;
import com.edgewave.net.SkillPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class Edgewave implements ModInitializer {
    public static final String ID = "edgewave";

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        ModParticles.init();
        Effects.init();
        ModItems.init();
        PayloadTypeRegistry.playC2S().register(SkillPayload.ID, SkillPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CdPayload.ID, CdPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkillPayload.ID, (payload, context) -> Skills.use(context.player(), payload.slot()));
    }
}
