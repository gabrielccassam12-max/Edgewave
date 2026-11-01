package com.edgewave.client;

import com.edgewave.particle.CubeProfile;
import com.edgewave.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.particle.SimpleParticleType;

import java.util.Map;

public class EdgeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            CubeProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new CubeParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }
    }
}
