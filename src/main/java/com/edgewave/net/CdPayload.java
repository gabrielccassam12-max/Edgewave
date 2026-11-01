package com.edgewave.net;

import com.edgewave.Edgewave;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> player: this skill is now on cooldown for N ticks. */
public record CdPayload(int slot, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<CdPayload> ID = new CustomPayload.Id<>(Edgewave.id("cd"));
    public static final PacketCodec<RegistryByteBuf, CdPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CdPayload::slot,
            PacketCodecs.VAR_INT, CdPayload::ticks,
            CdPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
