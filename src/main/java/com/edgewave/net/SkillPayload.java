package com.edgewave.net;

import com.edgewave.Edgewave;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Player -> server: a skill key was pressed (slot 0..5). */
public record SkillPayload(int slot) implements CustomPayload {
    public static final CustomPayload.Id<SkillPayload> ID = new CustomPayload.Id<>(Edgewave.id("skill"));
    public static final PacketCodec<RegistryByteBuf, SkillPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SkillPayload::slot, SkillPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
