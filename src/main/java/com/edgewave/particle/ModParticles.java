package com.edgewave.particle;

import com.edgewave.Edgewave;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cube particles of the blade: white -> energy blue -> deep blue. Names match assets/edgewave/particles/<name>.json. */
public final class ModParticles {
    private ModParticles() {}

    public static final Map<String, CubeProfile> PROFILES = new LinkedHashMap<>();
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    static {
        //                                    young     middle    old       size   var    life var grav  drag   shrink spin  flat  alpha inner
        PROFILES.put("e_arc",   new CubeProfile(0xF2FDFF, 0x5CD0FF, 0x1A58D0, 0.17f, 0.03f, 7,  2, 0.0f,  0.85f, 0.900f, 6f,  0.30f, 0.90f, false));
        PROFILES.put("e_core",  new CubeProfile(0xFFFFFF, 0xBFF0FF, 0x3AA8FF, 0.40f, 0.10f, 6,  2, 0.0f,  0.80f, 0.870f, 20f, 1f,    0.95f, true));
        PROFILES.put("e_glow",  new CubeProfile(0xFFFFFF, 0x4CCBFF, 0x1060D0, 0.22f, 0.10f, 12, 5, 0.0f,  0.90f, 0.940f, 14f, 1f,    0.75f, true));
        PROFILES.put("e_spark", new CubeProfile(0xFFFFFF, 0x8AE4FF, 0x2C7CFF, 0.07f, 0.03f, 16, 6, 0.02f, 0.92f, 0.970f, 40f, 1f,    1.00f, false));
        PROFILES.put("e_bit",   new CubeProfile(0xF0F6FF, 0x9AA6B4, 0x2A3140, 0.09f, 0.04f, 20, 8, 0.0f,  0.90f, 0.970f, 30f, 1f,    0.95f, false));
    }

    public static SimpleParticleType get(String name) {
        return TYPES.get(name);
    }

    public static void init() {
        for (String name : PROFILES.keySet()) {
            SimpleParticleType t = FabricParticleTypes.simple(true);
            Registry.register(Registries.PARTICLE_TYPE, Edgewave.id(name), t);
            TYPES.put(name, t);
        }
    }
}
