package dev.error404.anomaly;

import java.util.function.Supplier;

import dev.error404.anomaly.builders.ArchiveAnomaly;
import dev.error404.anomaly.builders.ChunkSeamAnomaly;
import dev.error404.anomaly.builders.DoorToRoomAnomaly;
import dev.error404.anomaly.builders.HalfHouseAnomaly;
import dev.error404.anomaly.builders.HeartbeatVaultAnomaly;
import dev.error404.anomaly.builders.InvertedTreeAnomaly;
import dev.error404.anomaly.builders.LastChunkAnomaly;
import dev.error404.anomaly.builders.LoadBarWallAnomaly;
import dev.error404.anomaly.builders.MemoryOfHomeAnomaly;
import dev.error404.anomaly.builders.MirrorVillageAnomaly;
import dev.error404.anomaly.builders.SinkholeAnomaly;
import dev.error404.anomaly.builders.StaircaseAnomaly;

/**
 * The twelve structures that grow out of a dying world.
 *
 * <p>Each entry carries its error code (shown in chat + the Error Log), the minimum phase
 * it can appear in, and a builder that places it procedurally.</p>
 */
public enum AnomalyType {
	NULL_HUT("ERR_404_A", 2, HalfHouseAnomaly::new),
	STAIRCASE_TO_NOWHERE("ERR_404_B", 2, StaircaseAnomaly::new),
	CHUNK_SEAM("ERR_404_C", 2, ChunkSeamAnomaly::new),
	MIRROR_VILLAGE("ERR_404_D", 3, MirrorVillageAnomaly::new),
	LOADBAR_WALL("ERR_404_E", 3, LoadBarWallAnomaly::new),
	DOOR_TO_ROOM("ERR_404_F", 3, DoorToRoomAnomaly::new),
	THE_SINKHOLE("ERR_404_G", 3, SinkholeAnomaly::new),
	ARCHIVE("ERR_404_H", 3, ArchiveAnomaly::new),
	INVERTED_TREE("ERR_404_I", 3, InvertedTreeAnomaly::new),
	MEMORY_OF_HOME("ERR_404_J", 4, MemoryOfHomeAnomaly::new),
	THE_LAST_CHUNK("ERR_404_K", 4, LastChunkAnomaly::new),
	HEARTBEAT_VAULT("ERR_404_X", 4, HeartbeatVaultAnomaly::new);

	private final String code;
	private final int minimumPhase;
	private final Supplier<Anomaly> factory;

	AnomalyType(String code, int minimumPhase, Supplier<Anomaly> factory) {
		this.code = code;
		this.minimumPhase = minimumPhase;
		this.factory = factory;
	}

	public String code() {
		return code;
	}

	public int minimumPhase() {
		return minimumPhase;
	}

	public Anomaly create() {
		return factory.get();
	}

	public static AnomalyType byCode(String code) {
		for (AnomalyType type : values()) {
			if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
				return type;
			}
		}

		return null;
	}
}
