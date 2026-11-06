package dev.error404.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

/**
 * The chat is a chat.
 *
 * <p>Other horror mods fake a crash screen, which is a lie the player can check. This is the
 * opposite: the client occasionally types in chat <em>as the player</em>, in first person, about
 * things the player has not done yet. Nothing is executed, nothing is sent to the server - it
 * is a client-side line in the player's own voice, and that is the entire trick. There is no
 * way to prove the mod wrote it, and that is the point.</p>
 *
 * <p>Rules, to keep it from becoming spam or abuse:</p>
 * <ul>
 *   <li>client-only: never sent to the server, so it cannot be mistaken for a real player</li>
 *   <li>at most one line per 4-9 minutes of play, and never in the first five minutes</li>
 *   <li>never on a death screen, never twice in a row, never the same line twice in a session</li>
 *   <li>silent in `chill` and when the mod is disabled: a player who turned the horror down
 *       should not be gaslit</li>
 * </ul>
 */
public final class SentientChat {
	private SentientChat() {
	}

	/** Lines that read as the player's own typing, past tense, slightly wrong. */
	private static final String[] LINES = {
			"i thought i closed that door",
			"did anyone else hear that",
			"why is my bed on the other side",
			"i did not write that down",
			"the map is missing the part i just walked",
			"i keep ending up in the same chunk",
			"has anyone seen my dog",
			"i have been standing still for ten minutes",
			"it is behind the tree",
			"i am not alone in here",
			"the coordinates changed",
			"i built this wall. i did not build this door.",
	};

	private static final Deque<String> SPOKEN = new ArrayDeque<>();

	private static int ticks;
	private static int nextIn;
	private static final Random RANDOM = new Random();

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SentientChat::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.screen instanceof DeathScreen) {
			return;
		}

		if (!dev.error404.Error404Config.get().enabled || !dev.error404.Error404Config.get().chaos) {
			return;
		}

		if (dev.error404.Difficulty.current() == dev.error404.Difficulty.CHILL) {
			return;
		}

		// the world has to have started falling apart before it starts talking
		if (ClientState.phase() < 2 || ClientState.integrity() > 70) {
			return;
		}

		ticks++;

		if (nextIn == 0) {
			nextIn = (20 * 60 * 4) + RANDOM.nextInt(20 * 60 * 5);
		}

		if (ticks < nextIn) {
			return;
		}

		ticks = 0;
		nextIn = (20 * 60 * 4) + RANDOM.nextInt(20 * 60 * 5);

		String line = pick();

		if (line == null) {
			return;
		}

		// as the player, in the player's own name, in chat - client side only
		client.player.displayClientMessage(
				Component.literal("<" + client.player.getGameProfile().name() + "> " + line)
						.withStyle(ChatFormatting.DARK_GRAY), false);

		client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
				dev.error404.ModSounds.DATA_CHIRP, net.minecraft.sounds.SoundSource.AMBIENT, 0.25f, 0.6f, false);
	}

	private static String pick() {
		if (SPOKEN.size() >= LINES.length) {
			return null;
		}

		for (int attempt = 0; attempt < 12; attempt++) {
			String candidate = LINES[RANDOM.nextInt(LINES.length)];

			if (!SPOKEN.contains(candidate)) {
				SPOKEN.add(candidate);
				return candidate;
			}
		}

		return null;
	}

	/** Called on disconnect so a new world gets the full pool back. */
	public static void reset() {
		SPOKEN.clear();
		ticks = 0;
		nextIn = 0;
	}
}
