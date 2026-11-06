package dev.error404.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import dev.error404.Error404;

/** integrity (0-100), phase ordinal, session seed, fragments collected, ended flag, flags. */
public record StateSyncPayload(int integrity, int phase, long seed, int fragments, boolean ended, int flags)
		implements CustomPacketPayload {
	/** Config bits mirrored to the client: 1 = photosensitivity, 2 = HUD readout. */
	public static final int FLAG_PHOTOSENSITIVITY = 1;
	public static final int FLAG_HUD = 2;
	public static final CustomPacketPayload.Type<StateSyncPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Error404.MOD_ID, "state_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StateSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StateSyncPayload::integrity,
			ByteBufCodecs.VAR_INT, StateSyncPayload::phase,
			ByteBufCodecs.VAR_LONG, StateSyncPayload::seed,
			ByteBufCodecs.VAR_INT, StateSyncPayload::fragments,
			ByteBufCodecs.BOOL, StateSyncPayload::ended,
			ByteBufCodecs.VAR_INT, StateSyncPayload::flags,
			StateSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
