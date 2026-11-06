package dev.error404;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards the mod's advancements.
 *
 * <p>They cannot be driven by JSON triggers alone: "committed eight fragments" and "the world
 * ended" are server-side facts, not inventory states, so the JSON criteria sit at
 * {@code minecraft:impossible} and this class is the only thing that grants them. That keeps
 * the criteria honest - an advancement that says "you did X" fires exactly when X happened.</p>
 */
public final class Achievements {
	private Achievements() {
	}

	public static final String ROOT = "root";
	public static final String FIRST_SIGNAL = "first_signal";
	public static final String FIRST_FRAGMENT = "first_fragment";
	public static final String ANCHOR_DOWN = "anchor_down";
	public static final String ENDING_SALVAGE = "ending_salvage";
	public static final String ENDING_DELETE = "ending_delete";
	public static final String ENDING_NULL = "ending_null";

	/** Silent no-op if the datapack entry is missing or the player already has it. */
	public static void grant(ServerPlayer player, String name) {
		if (player == null || player.level().getServer() == null) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		AdvancementHolder holder = server.getAdvancements().get(Error404.id("error404/" + name));

		if (holder == null) {
			Error404.LOGGER.debug("[error404] advancement {} is not loaded", name);
			return;
		}

		for (String criterion : holder.value().criteria().keySet()) {
			player.getAdvancements().award(holder, criterion);
		}
	}
}
