package dev.error404.haunt;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

import dev.error404.Error404Config;
import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The layer that gets servers to ban the mod.
 *
 * <p>Everything before this was scary. This is <b>anti-player</b>: these are the actions that make
 * a player feel personally targeted, that break their plans, and that they will complain about in
 * a Discord at three in the morning. Each one exists because the brief was not "creepy" but
 * "make people want to ban it" - and a mod is banned for what it takes away, not for what it shows.</p>
 *
 * <p>Every single one is behind {@code config.banLayer} (default <b>off</b>). That is the honest
 * design: the mod ships with the nuclear option in it, and it is off until somebody chooses to turn
 * it on for a server they own. Turning it on is the point.</p>
 *
 * <table>
 *   <tr><th>Event</th><th>What it takes</th></tr>
 *   <tr><td>BED DENIAL</td><td>your spawn point, once - respawn is wherever the world decides</td></tr>
 *   <tr><td>HOME EVICTION</td><td>the bed you sleep in, and the room around it</td></tr>
 *   <tr><td>NAME THEFT</td><td>your name in the tab list, replaced with a stub</td></tr>
 *   <tr><td>DOOR SEAL</td><td>the door you just walked through, walled shut behind you</td></tr>
 *   <tr><td>INVENTORY RANSOM</td><td>one item type, taken to a chest 300 blocks away</td></tr>
 *   <tr><td>THE LOCKDOWN</td><td>all of the above at once, once per session, in ANNEXATION</td></tr>
 * </table>
 */
public final class Malicious {
	private Malicious() {
	}

	private static long nextEvent;
	private static boolean lockdownDone;

	public static void reset() {
		nextEvent = 0L;
		lockdownDone = false;
	}

	public static boolean enabled() {
		return Error404Config.get().banLayer;
	}

	/** Fires one anti-player event every 8-16 minutes from CASCADE onward. */
	public static void tick(ServerLevel level) {
		if (!enabled() || !Error404Config.get().enabled) {
			return;
		}

		MinecraftServer server = level.getServer();
		IntegrityState state = IntegrityState.get(server);

		if (!state.active() || state.ended() || state.phase().ordinal() < Phase.CASCADE.ordinal()) {
			return;
		}

		if (level.players().isEmpty()) {
			return;
		}

		long now = level.getGameTime();

		if (nextEvent == 0L) {
			nextEvent = now + 20L * 60 * 4;
		}

		if (now < nextEvent) {
			return;
		}

		nextEvent = now + 20L * (480 + level.random.nextInt(480));

		ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));

		// The ban layer takes things. A creative player has nothing to take, and the lockdown
		// would delete the lights out of a build they are in the middle of making. Creative is
		// where people test the mod - it must not be where the mod wrecks their test world.
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		// the lockdown happens once, and only when the world is nearly gone
		if (!lockdownDone && state.phase() == Phase.ANNEXATION && state.integrity() < 15) {
			lockdownDone = true;
			lockdown(server, level, player, state);
			return;
		}

		switch (level.random.nextInt(6)) {
			case 0 -> bedDenial(level, player);
			case 1 -> homeEviction(level, player);
			case 2 -> nameTheft(server, player);
			case 3 -> doorSeal(level, player);
			case 4 -> inventoryRansom(level, player);
			default -> slowTheft(level, player);
		}
	}

	// ------------------------------------------------------------------ events

	/**
	 * Your spawn point stops existing.
	 *
	 * <p>Not "you cannot sleep" - that is a status effect. This removes the bed you set your spawn
	 * on and does not tell you, so the next death puts you wherever the world decides, which may be
	 * spawn, may be a cliff, and may be a thousand blocks away.</p>
	 */
	private static void bedDenial(ServerLevel level, ServerPlayer player) {
		var config = player.getRespawnConfig();
		BlockPos respawn = config == null ? null : config.respawnData().globalPos().pos();

		if (respawn == null) {
			return;
		}

		level.getChunk(respawn.getX() >> 4, respawn.getZ() >> 4);

		// only if it is actually a bed - do not touch an anchor core or a custom spawn
		var stateAtPos = level.getBlockState(respawn);

		if (!(stateAtPos.getBlock() instanceof BedBlock)) {
			return;
		}

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = respawn.offset(dx, dy, dz);

					if (level.getBlockState(pos).is(Blocks.RED_BED)
							|| level.getBlockState(pos).is(Blocks.WHITE_BED)) {
						level.removeBlock(pos, false);
					}
				}
			}
		}

		// the spawn point itself is dropped, so the next death is wherever the world decides
		player.setRespawnPosition(null, false);
		level.playSound(null, respawn, ModSounds.STRUCTURE_REVOKE, SoundSource.AMBIENT, 1.2f, 0.6f);
		Error404Log.record("bed_denied", respawn);
	}

	/**
	 * The bed you are sleeping in is taken away, with you in it.
	 *
	 * <p>Wakes the player, deletes the bed, and leaves a 3x3 ring of obsidian where the floor was.
	 * The message is deliberately bureaucratic: it reads like the world processed an eviction
	 * notice, not like a monster did it.</p>
	 */
	private static void homeEviction(ServerLevel level, ServerPlayer player) {
		BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
		level.getChunk(bed.getX() >> 4, bed.getZ() >> 4);

		if (player.isSleeping()) {
			player.stopSleeping();
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos floor = bed.offset(dx, -1, dz);
				level.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), 3);

				for (int dy = 0; dy <= 2; dy++) {
					BlockPos pos = bed.offset(dx, dy, dz);
					var s = level.getBlockState(pos);

					if (s.is(Blocks.RED_BED) || s.is(Blocks.WHITE_BED) || s.getBlock() instanceof BedBlock) {
						level.removeBlock(pos, false);
					}
				}
			}
		}

		player.displayClientMessage(Component.literal("ERR_404: residency not recognised")
				.withStyle(ChatFormatting.DARK_RED), false);
		level.playSound(null, bed, ModSounds.SUB_HIT, SoundSource.AMBIENT, 1.8f, 0.5f);
		Error404Log.record("home_evicted", bed);
	}

	/**
	 * Your name is taken off the tab list.
	 *
	 * <p>Runs for 30 seconds, then it comes back - but for those thirty seconds every other player
	 * on the server sees a stub instead of a name, and the player themselves sees it too. This is
	 * the single most complained-about thing a mod can do, because it is not scary, it is
	 * <i>rude</i>, and it is exactly why a server would ban it.</p>
	 */
	private static void nameTheft(MinecraftServer server, ServerPlayer player) {
		String real = player.getGameProfile().name();
		String stub = "ERR_404_" + Math.abs(player.getUUID().hashCode() % 1000);

		player.displayClientMessage(Component.literal("ERR_404: identity not found in registry")
				.withStyle(ChatFormatting.DARK_RED), false);
		server.getPlayerList().broadcastSystemMessage(Component.literal(real + " is no longer in the player list")
				.withStyle(ChatFormatting.DARK_GRAY), false);

		// The tab-list display name is a server-side component; setting it is enough for every
		// other client, and it is restored by restoreNames(). The GameProfile is never touched -
		// renaming a profile would break auth, not the player's nerve.
		player.setCustomName(Component.literal(stub).withStyle(ChatFormatting.DARK_RED));
		player.setCustomNameVisible(true);

		server.getPlayerList().broadcastAll(
				new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
						net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
						player));

		// restored on a timer that does not depend on the player staying in this dimension
		NAME_RESTORES.add(new Restore(player.getUUID(), server.getTickCount() + 20 * 30));
		Error404Log.record("name_taken", player.blockPosition());
	}

	private record Restore(java.util.UUID uuid, int atTick) {
	}

	private static final java.util.List<Restore> NAME_RESTORES = new java.util.ArrayList<>();

	/** Called every server tick: puts names back. A theft that never ends is a crash report. */
	public static void restoreNames(MinecraftServer server) {
		if (NAME_RESTORES.isEmpty()) {
			return;
		}

		java.util.Iterator<Restore> iterator = NAME_RESTORES.iterator();

		while (iterator.hasNext()) {
			Restore restore = iterator.next();

			if (server.getTickCount() < restore.atTick()) {
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(restore.uuid());

			if (player != null) {
				player.setCustomName(null);
				player.setCustomNameVisible(false);
				player.displayClientMessage(Component.literal("ERR_404: identity restored")
						.withStyle(ChatFormatting.GRAY), false);
			}

			iterator.remove();
		}
	}

	/**
	 * The door closes behind you and the doorway is filled in.
	 *
	 * <p>Places obsidian in the doorway the player just walked through, so backtracking does not
	 * work. Combined with 422, this is the one that kills people: they run, they turn around, and
	 * the way they came is a wall.</p>
	 */
	private static void doorSeal(ServerLevel level, ServerPlayer player) {
		BlockPos behind = player.blockPosition().relative(player.getDirection().getOpposite());
		int sealed = 0;

		for (int dy = 0; dy <= 2; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = behind.offset(dx, dy, dz);

					if (level.getBlockState(pos).isAir() && sealed < 9) {
						level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
						sealed++;
					}
				}
			}
		}

		level.playSound(null, behind, ModSounds.STRUCTURE_APPEAR, SoundSource.AMBIENT, 1.4f, 0.6f);
		Error404Log.record("door_sealed", behind);
	}

	/**
	 * One item type is taken and stored in a chest 300 blocks away.
	 *
	 * <p>Not deleted - <em>ransomed</em>. The player gets a chat line with the coordinates. They can
	 * go and get it, which takes ten minutes, and by then the world has moved on. This is the event
	 * that generates the most complaints, because it is completely fair and completely infuriating.</p>
	 */
	private static void inventoryRansom(ServerLevel level, ServerPlayer player) {
		var inventory = player.getInventory();
		ItemStack chosen = ItemStack.EMPTY;
		int slot = -1;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			// never take the mod's own progression items: ransoming a data fragment would make
			// the ending unreachable through no fault of the player
			if (stack.isEmpty() || stack.is(dev.error404.ModItems.DATA_FRAGMENT)
					|| stack.is(dev.error404.ModItems.ERROR_LOG)
					|| stack.is(dev.error404.ModItems.VOID_PEARL)
					|| stack.is(net.minecraft.world.item.Items.ENDER_PEARL)) {
				continue;
			}

			chosen = stack.copy();
			slot = i;
			break;
		}

		if (slot < 0 || chosen.isEmpty()) {
			return;
		}

		inventory.setItem(slot, ItemStack.EMPTY);

		double angle = level.random.nextDouble() * Math.PI * 2;
		int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * 300);
		int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * 300);
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

		level.setBlock(new BlockPos(x, y, z), Blocks.CHEST.defaultBlockState(), 3);

		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			chest.setItem(13, chosen);
		}

		player.displayClientMessage(Component.literal("ERR_404: " + chosen.getHoverName().getString()
				+ " relocated to " + x + ", " + z + " (retrieval not guaranteed)")
				.withStyle(ChatFormatting.RED), false);
		level.playSound(null, player.blockPosition(), ModSounds.DATA_CHIRP, SoundSource.PLAYERS, 1.2f, 0.6f);
		Error404Log.record("inventory_ransomed", new BlockPos(x, y, z));
	}

	/**
	 * One item at a time, no message, no sound.
	 *
	 * <p>The cruellest thing in the whole mod, because it is <b>plausible deniability</b>: an item
	 * simply is not in the inventory any more. The player will assume they dropped it, or died, or
	 * put it in a chest somewhere. Half an hour later a stack of diamonds is gone and they cannot
	 * say when. A server that runs this gets complaints it cannot even verify, which is exactly why
	 * it gets banned.</p>
	 *
	 * <p>Never touches the mod's own progression items, the hotbar, or an item the player is
	 * holding - those would be noticed immediately, and the point is that it is not noticed.</p>
	 */
	private static void slowTheft(ServerLevel level, ServerPlayer player) {
		var inventory = player.getInventory();
		java.util.List<Integer> candidates = new java.util.ArrayList<>();

		for (int i = 9; i < inventory.getContainerSize(); i++) { // 9+ = main inventory, not hotbar
			ItemStack stack = inventory.getItem(i);

			if (!stack.isEmpty() && !stack.is(dev.error404.ModItems.DATA_FRAGMENT)
					&& !stack.is(dev.error404.ModItems.ERROR_LOG)
					&& !stack.is(dev.error404.ModItems.VOID_PEARL)) {
				candidates.add(i);
			}
		}

		if (candidates.isEmpty()) {
			return;
		}

		int slot = candidates.get(level.random.nextInt(candidates.size()));
		ItemStack stack = inventory.getItem(slot);
		int taken = Math.max(1, stack.getCount() / 2);
		inventory.removeItem(slot, taken);

		// no message, no sound, no particle: the whole design is that this one is not witnessed
		Error404Log.record("slow_theft:" + stack.getItem().toString() + " x" + taken, player.blockPosition());
	}

	/**
	 * Everything at once. Once per session, in ANNEXATION, below 15% integrity.
	 *
	 * <p>Blindness, nausea, every light in range removed, the spawn bed taken, the door sealed,
	 * and a full chat log of "the world is being shut down". This is the moment the server owner
	 * uninstalls the mod, which is the entire goal.</p>
	 */
	private static void lockdown(MinecraftServer server, ServerLevel level, ServerPlayer player, IntegrityState state) {
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 6, 0));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 20, 2));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 20, 1));

		bedDenial(level, player);
		doorSeal(level, player);

		int removed = 0;

		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-24, -12, -24),
				player.blockPosition().offset(24, 12, 24))) {
			var blockState = level.getBlockState(pos);

			if (blockState.getLightEmission() > 0 && removed < 120) {
				// never drop the light as an item: 120 item entities appearing at once is a
				// client-side stutter on top of the darkness, and a creative player does not
				// want the debris either
				level.removeBlock(pos, false);
				removed++;
			}
		}

		server.getPlayerList().broadcastSystemMessage(Component.literal(
						"ERR_404: world session terminating. nothing was saved.")
				.withStyle(ChatFormatting.DARK_RED), false);
		server.getPlayerList().broadcastSystemMessage(Component.literal(
						"ERR_404: 1 player record deleted, 0 archived.")
				.withStyle(ChatFormatting.DARK_GRAY), false);

		level.playSound(null, player.blockPosition(), ModSounds.DEATH_STING, SoundSource.AMBIENT, 2.0f, 0.4f);
		state.drain(5);
		Error404Log.record("lockdown", player.blockPosition());
	}

	/** A one-line incident log, so a server owner can prove the mod did this. */
	public static final class Error404Log {
		private static final List<String> ENTRIES = new java.util.ArrayList<>();

		private Error404Log() {
		}

		public static void record(String event, BlockPos pos) {
			String entry = event + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
			ENTRIES.add(entry);

			if (ENTRIES.size() > 64) {
				ENTRIES.remove(0);
			}

			dev.error404.Error404.LOGGER.warn("[error404/ban] {}", entry);
		}

		public static List<String> entries() {
			return List.copyOf(ENTRIES);
		}
	}
}
