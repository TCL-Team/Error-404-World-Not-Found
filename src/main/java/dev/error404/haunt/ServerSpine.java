package dev.error404.haunt;

import net.minecraft.server.MinecraftServer;

import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The part of the mod that does not happen to a player: it changes the <b>server</b>.
 *
 * <p>Every other system in this mod can be escaped by leaving. This one cannot, because it attacks
 * the thing everyone checks before they even connect:</p>
 *
 * <ul>
 *   <li><b>The MOTD lies.</b> In DELETION the server starts advertising itself as
 *       {@code ERROR 404: World Not Found}. A player refreshing their multiplayer list sees the
 *       server renamed itself while they were playing, which is far more unsettling than anything
 *       that can happen in game, because it means the mod is not contained by the world.</li>
 *   <li><b>The player count lies too.</b> The MOTD says how many records are left to process, and
 *       that number only goes down.</li>
 * </ul>
 *
 * <p>Restored on {@code /error404 restore}, on stop, and whenever the mod is turned off. That undo
 * is not a courtesy - it is the difference between a mod that gets blacklisted and a mod that gets
 * somebody banned from their own hosting.</p>
 */
public final class ServerSpine {
	private ServerSpine() {
	}

	private static String originalMotd;
	private static boolean motdTaken;
	private static long lastMotdTick;

	/** Called once per second. Cheap: two setters, no allocation except the patched string. */
	public static void tick(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);

		if (!dev.error404.Error404Config.get().enabled || !state.active() || state.ended()) {
			restore(server);
			return;
		}

		if (state.phase().ordinal() < Phase.DELETION.ordinal()) {
			restore(server);
			return;
		}

		if (server.getTickCount() - lastMotdTick < 20L * 10L) {
			return;
		}

		lastMotdTick = server.getTickCount();

		if (!motdTaken) {
			originalMotd = server.getMotd();
			motdTaken = true;
			dev.error404.Error404.LOGGER.warn("[error404] server MOTD captured: {}", originalMotd);
		}

		// Stable per phase, not random: a motd that flickers reads as a bug, a motd that is wrong
		// and stays wrong reads as the server being gone. The record count only ever goes down.
		server.setMotd(switch (state.phase()) {
			case DELETION -> "ERR_404 // region deleted // " + state.integrity() + "% remaining";
			case CASCADE -> "ERR_404 // cascade failure // " + state.integrity() + "% remaining";
			case ANNEXATION -> "ERROR 404: World Not Found";
			default -> originalMotd == null ? server.getMotd() : originalMotd;
		});
	}

	/** Puts the server's own metadata back. Safe to call at any time, from anywhere. */
	public static void restore(MinecraftServer server) {
		if (!motdTaken) {
			return;
		}

		motdTaken = false;

		if (originalMotd != null) {
			server.setMotd(originalMotd);
		}

		originalMotd = null;
	}

	/** Puts the world back in the server-list sense too; called from /error404 restore. */
	public static void reset(MinecraftServer server) {
		motdTaken = false;
		originalMotd = null;
		lastMotdTick = 0L;
		restore(server);
	}
}
