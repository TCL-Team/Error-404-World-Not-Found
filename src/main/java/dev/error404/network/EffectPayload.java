package dev.error404.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import dev.error404.Error404;

/** kind: 0 static, 1 phase shift, 2 message, 3 clear, 4 desaturate. */
public record EffectPayload(int kind, String message, int durationTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EffectPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Error404.MOD_ID, "effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EffectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EffectPayload::kind,
			ByteBufCodecs.STRING_UTF8, EffectPayload::message,
			ByteBufCodecs.VAR_INT, EffectPayload::durationTicks,
			EffectPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
