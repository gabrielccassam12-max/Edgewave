package com.edgewave;

import com.edgewave.item.ModItems;
import com.edgewave.particle.ModParticles;
import com.edgewave.util.Effects;
import net.fabricmc.api.ModInitializer;
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
    }
}
