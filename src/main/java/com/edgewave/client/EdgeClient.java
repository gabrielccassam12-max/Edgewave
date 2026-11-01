package com.edgewave.client;

import com.edgewave.combat.Skills;
import com.edgewave.item.ModItems;
import com.edgewave.net.CdPayload;
import com.edgewave.net.SkillPayload;
import com.edgewave.particle.CubeProfile;
import com.edgewave.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class EdgeClient implements ClientModInitializer {
    private static final KeyBinding[] KEYS = new KeyBinding[Skills.COUNT];
    private static final boolean[] down = new boolean[Skills.COUNT];
    private static final long[] cdEnd = new long[Skills.COUNT];
    private static long clock;

    @Override
    public void onInitializeClient() {
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            CubeProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new CubeParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }

        int[] codes = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N};
        for (int i = 0; i < KEYS.length; i++)
            KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.edgewave.skill" + (i + 1), InputUtil.Type.KEYSYM, codes[i], "key.categories.edgewave"));

        ClientPlayNetworking.registerGlobalReceiver(CdPayload.ID, (payload, context) -> {
            if (payload.slot() >= 0 && payload.slot() < cdEnd.length) cdEnd[payload.slot()] = clock + payload.ticks();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clock++;
            if (client.player == null) return;
            boolean free = client.currentScreen == null;
            for (int i = 0; i < KEYS.length; i++) {
                boolean now = free && KEYS[i].isPressed();
                if (now && !down[i]) ClientPlayNetworking.send(new SkillPayload(i));
                down[i] = now;
                while (KEYS[i].wasPressed()) { }
            }
        });

        HudRenderCallback.EVENT.register(EdgeClient::hud);
    }

    private static void hud(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !mc.player.getMainHandStack().isOf(ModItems.EDGE_SWORD)) return;
        int w = 112, x = g.getScaledWindowWidth() - w - 10, y = g.getScaledWindowHeight() - 30 - Skills.COUNT * 12;
        for (int i = 0; i < Skills.COUNT; i++) {
            int ry = y + i * 12;
            long left = Math.max(0, cdEnd[i] - clock);
            g.fill(x - 3, ry - 1, x + w + 3, ry + 10, 0x88000000);
            int col = left > 0 ? 0xFF8A94A0 : 0xFFBFF0FF;
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(KEYS[i].getBoundKeyLocalizedText()).append("] ").append(Text.translatable("skill.edgewave." + i)), x, ry + 1, col);
            if (left > 0) {
                String s = String.format("%.1f", left / 20.0);
                g.drawTextWithShadow(mc.textRenderer, s, x + w - mc.textRenderer.getWidth(s), ry + 1, 0xFFFF8888);
                int cw = (int) (w * (1f - (float) left / Skills.COOLDOWN[i]));
                g.fill(x, ry + 9, x + cw, ry + 10, 0xFF58C8FF);
            }
        }
    }
}
