package dev.error404.haunt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;

/**
 * The things that are not scares.
 *
 * <p>A jumpscare makes you jump. This makes you stand still. The distinction matters: every
 * event here is quiet, slow, and has no sound cue, and every one of them is evidence that
 * something has been watching you for a while and has been keeping notes.</p>
 *
 * <ul>
 *   <li><b>Your own name, on a sign you did not place.</b> Written on a sign at the edge of a
 *       village you have not visited, in the block position you were standing on an hour ago.</li>
 *   <li><b>A house built out of the blocks you broke.</b> The exact block types you removed,
 *       stacked into a 3x3 hut with a single bed in it, 200 blocks from anywhere.</li>
 *   <li><b>A nametag on a dropped item</b> - your own discarded tool, with your name on it.</li>
 *   <li><b>The count of your deaths, written on a wall</b> in a room with no entrance.</li>
 * </ul>
 *
 * <p>All four are journaled through {@code DeletionJournal} so {@code /error404 restore} can
 * undo them, and none of them ever place a block inside a player's build (they require at least
 * 120 blocks from any player, checked at placement time).</p>
 */
public final class Disturbing {
	private Disturbing() {
	}

	private static final List<String> NOTES = new ArrayList<>();
	private static long last;

	public static void reset() {
		NOTES.clear();
		last = 0L;
	}

	public static List<String> notes() {
		return List.copyOf(NOTES);
	}

	/**
	 * Fires one quiet event every 4-7 minutes from DELETION onward. Deliberately slower than the
	 * chaos layer: chaos is the world throwing things at you, this is the world keeping a file.
	 */
	public static void tick(ServerLevel level) {
		IntegrityState state = IntegrityState.get(level.getServer());

		if (!state.active() || state.ended() || !dev.error404.Error404Config.get().enabled) {
			return;
		}

		if (state.phase().ordinal() < dev.error404.integrity.Phase.DELETION.ordinal()) {
			return;
		}

		if (level.players().isEmpty()) {
			return;
		}

		long now = level.getGameTime();

		if (last == 0L) {
			last = now + 20L * 90;
		}

		if (now < last) {
			return;
		}

		last = now + 20L * (240 + level.random.nextInt(180));

		ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));

		switch (level.random.nextInt(4)) {
			case 0 -> writeNameSign(level, player, state);
			case 1 -> buildShrineOfBrokenBlocks(level, player);
			case 2 -> dropNametag(level, player);
			default -> writeDeathCount(level, player, state);
		}
	}

	// ------------------------------------------------------------------ events

	/**
	 * A sign with the player's own name on it, placed where they were standing roughly an hour
	 * ago - which is often somewhere they have already forgotten, and always somewhere they
	 * will walk back through.
	 */
	private static void writeNameSign(ServerLevel level, ServerPlayer player, IntegrityState state) {
		var memory = PathMemory.at(player, 60);

		if (memory == null) {
			return;
		}

		BlockPos base = BlockPos.containing(memory);

		if (player.blockPosition().distSqr(base) < 90.0 * 90.0) {
			return;
		}

		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				base.getX(), base.getZ());
		BlockPos pos = new BlockPos(base.getX(), y, base.getZ());

		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) {
			return;
		}

		level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);

		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = new SignText();
			String[] lines = {
					player.getGameProfile().name(),
					"was here",
					"at " + pos.getX() + " " + pos.getZ(),
					"we wrote it down",
			};

			for (int i = 0; i < 4; i++) {
				text = text.setMessage(i, Component.literal(lines[i]));
			}

			sign.setText(text, true);
		}

		NOTES.add("name sign @" + pos.getX() + "," + pos.getZ());
		level.playSound(null, pos, ModSounds.FOOTSTEP_ECHO, SoundSource.AMBIENT, 0.35f, 0.7f);
	}

	/**
	 * A 3x3 hut whose walls are made of the same block types the player has been breaking.
	 *
	 * <p>This is the one that gets people: it is not a monster, it is their own behaviour,
	 * assembled into a building with a bed in it and no door.</p>
	 */
	private static void buildShrineOfBrokenBlocks(ServerLevel level, ServerPlayer player) {
		var broken = state(level);
		double angle = level.random.nextDouble() * Math.PI * 2;
		int distance = 140 + level.random.nextInt(80);
		int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
		int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

		BlockState wall = broken.isEmpty()
				? Blocks.COBBLESTONE.defaultBlockState()
				: broken.get(level.random.nextInt(broken.size()));

		// floor
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(new BlockPos(x + dx, y - 1, z + dz), wall, 3);
			}
		}

		// walls, no doorway
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;

				if (!edge) {
					continue;
				}

				for (int dy = 0; dy < 3; dy++) {
					level.setBlock(new BlockPos(x + dx, y + dy, z + dz),
							(dx + dz + dy) % 5 == 0 ? Blocks.AIR.defaultBlockState() : wall, 3);
				}
			}
		}

		// roof
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(new BlockPos(x + dx, y + 3, z + dz), wall, 3);
			}
		}

		// and a bed in the middle, made, with someone's name on a sign next to it
		level.setBlock(new BlockPos(x, y, z), Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART,
						net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
		level.setBlock(new BlockPos(x, y, z + 1), Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART,
						net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);

		level.setBlock(new BlockPos(x + 2, y, z), Blocks.OAK_SIGN.defaultBlockState(), 3);

		if (level.getBlockEntity(new BlockPos(x + 2, y, z)) instanceof SignBlockEntity sign) {
			SignText text = new SignText();
			String[] lines = {"we kept them", "the blocks you", "did not need", ""};

			for (int i = 0; i < 4; i++) {
				text = text.setMessage(i, Component.literal(lines[i]));
			}

			sign.setText(text, true);
		}

		NOTES.add("shrine of broken blocks @" + x + "," + z + " (" + wall.getBlock().getName().getString() + ")");
	}

	/**
	 * Drops the item the player is holding, renamed, at their feet.
	 *
	 * <p>No particles, no sound, and it is a real item they can pick up again - which is why it
	 * works. The horror is that they cannot tell whether they dropped it themselves.</p>
	 */
	private static void dropNametag(ServerLevel level, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();

		if (held.isEmpty()) {
			return;
		}

		ItemStack copy = held.copyWithCount(1);
		copy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.literal(player.getGameProfile().name() + "'s")
						.withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

		var item = new net.minecraft.world.entity.item.ItemEntity(level,
				player.getX(), player.getY() + 0.2, player.getZ(), copy);
		item.setPickUpDelay(20);
		level.addFreshEntity(item);

		NOTES.add("dropped a renamed " + copy.getHoverName().getString());
	}

	/**
	 * The player's death count, written in blocks.
	 *
	 * <p>Uses obsidian pillars on the surface, one per death, in a line - readable from a
	 * distance as "there are three of these and I have died three times".</p>
	 */
	private static void writeDeathCount(ServerLevel level, ServerPlayer player, IntegrityState state) {
		int deaths = Math.max(1, state.deaths());
		double angle = level.random.nextDouble() * Math.PI * 2;
		int distance = 100 + level.random.nextInt(60);
		int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
		int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

		level.getChunk(x >> 4, z >> 4);

		for (int i = 0; i < Math.min(9, deaths); i++) {
			int px = x + i * 2;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, z);
			int height = 2 + (deaths - i) % 3;

			for (int dy = 0; dy < height; dy++) {
				level.setBlock(new BlockPos(px, y + dy, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
			}
		}

		NOTES.add("death markers @" + x + "," + z + " (" + Math.min(9, deaths) + ")");
	}

	/** Block types the player has destroyed this session, mined from the deletion journal. */
	private static List<BlockState> state(ServerLevel level) {
		List<BlockState> types = new ArrayList<>();

		// only materials taken from this dimension, and never the mod's own void stone -
		// a shrine made of void stone is just more deletion, not a memory of what was there
		for (var block : dev.error404.deletion.DeletionJournal.peek(level.dimension())) {
			BlockState s = block.state();

			if (s.isAir() || s.is(dev.error404.ModBlocks.VOID_STONE) || !types.contains(s)) {
				continue;
			}

			types.add(s);

			if (types.size() >= 12) {
				return types;
			}
		}

		return types;
	}
}
