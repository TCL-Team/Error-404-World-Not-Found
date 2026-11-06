package dev.error404.commands;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

import dev.error404.Error404Config;
import dev.error404.integrity.IntegrityState;

/**
 * Commands are hidden, and that is the point.
 *
 * <p>Every other horror mod ships a command tree a player can read: {@code /summon}, {@code /chaos},
 * {@code /phase}. Reading it tells you exactly what the thing is going to do, which is half the
 * scare gone. So the whole {@code /error404} tree is registered but <b>invisible</b> until someone
 * proves they are the person running the mod:</p>
 *
 * <ul>
 *   <li>a server operator (permission level 2) always sees it,</li>
 *   <li>a singleplayer host with cheats on sees it,</li>
 *   <li>anybody can reveal it with {@code /error404 enable} - the one un-gated command, because
 *       otherwise a player on a server they do not administer has no way in at all.</li>
 * </ul>
 *
 * <p>The gate is a session flag, not a config value: it resets when the server stops, so a player
 * who revealed the tree yesterday does not walk into a visible command list today.</p>
 */
public final class CommandGate {
	private CommandGate() {
	}

	private static final Set<UUID> ENABLED = new HashSet<>();

	/** True when this source may see and run the tree. */
	public static boolean visible(CommandSourceStack source) {
		if (source.getServer() != null && source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
			return true;
		}

		if (source.getEntity() instanceof Player player) {
			return ENABLED.contains(player.getUUID());
		}

		// console (no entity) is always trusted; command blocks are not
		return source.getEntity() == null && source.getServer() != null;
	}

	/** Reveals the tree for one player, and turns the mod's runtime switch on with it. */
	public static boolean enable(CommandSourceStack source) {
		if (!(source.getEntity() instanceof Player player)) {
			return false;
		}

		ENABLED.add(player.getUUID());
		Error404Config.get().enabled = true;
		Error404Config.save();
		return true;
	}

	public static boolean disable(CommandSourceStack source) {
		if (source.getEntity() instanceof Player player) {
			ENABLED.remove(player.getUUID());
			return true;
		}

		return false;
	}

	public static void reset() {
		ENABLED.clear();
	}

	public static boolean runtimeEnabled(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);
		return Error404Config.get().enabled && state.active() && !state.ended();
	}

	public static String statusLine(MinecraftServer server) {
		return "mod " + (runtimeEnabled(server) ? "active" : "idle")
				+ ", commands " + (ENABLED.isEmpty() ? "hidden"
				: "revealed for " + ENABLED.size() + " player(s)")
				+ ", difficulty " + Error404Config.get().difficulty.toLowerCase(Locale.ROOT);
	}
}
