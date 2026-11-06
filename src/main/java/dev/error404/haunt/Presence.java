package dev.error404.haunt;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

import dev.error404.Error404Config;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The sound of somebody else in the room.
 *
 * <p>There is no entity here. Nothing in this class spawns, moves, or can be hit. It plays
 * <b>vanilla</b> sounds at positions that a real person would occupy - the floor behind you, the
 * door you just walked past, the space under the bed - and the player's own brain does the rest.
 * That is why it works, and it is why it survives being recorded: a clip of this mod is a clip of
 * footsteps and a person getting scared of nothing.</p>
 *
 * <p>Three cues, each on its own slow timer, never together:</p>
 *
 * <ul>
 *   <li><b>Footsteps</b> - four steps that walk up behind the player and stop. If the player turns
 *       around, there is nothing. If they do not, nothing happens either - the absence is the
 *       scare. Works in every dimension, over any block, because it is a sound event.</li>
 *   <li><b>Knocking</b> - three knocks on the nearest door within 20 blocks, in the direction the
 *       player is <em>not</em> looking.</li>
 *   <li><b>Breathing</b> - the player's own breath, played slightly to one side and slightly
 *       louder than it should be, when they have been standing still for a while.</li>
 * </ul>
 *
 * <p>All three respect anchor zones (an anchor muffles presence entirely), the {@code chill}
 * preset, and the same phase gate as everything else: nothing before DELETION, because a footstep
 * in the first two minutes is noise, and a footstep after twenty is a person.</p>
 */
public final class Presence {
	private Presence() {
	}

	/** A sound waiting for its turn, so a cue can be staged over a few ticks. */
	private record Cue(ServerLevel level, double x, double y, double z,
			net.minecraft.sounds.SoundEvent sound, SoundSource source, float volume, float pitch, int atTick) {
	}

	private static final java.util.Deque<Cue> QUEUE = new java.util.ArrayDeque<>();

	private static long nextFootsteps;
	private static long nextKnock;
	private static long nextBreath;
	private static BlockPos lastPos;
	private static int stillTicks;

	public static void reset() {
		QUEUE.clear();
		nextFootsteps = 0L;
		nextKnock = 0L;
		nextBreath = 0L;
		lastPos = null;
		stillTicks = 0;
	}

	public static void tick(ServerLevel level) {
		Error404Config cfg = Error404Config.get();

		if (!cfg.enabled || level.players().isEmpty()) {
			return;
		}

		IntegrityState state = IntegrityState.get(level.getServer());

		if (!state.active() || state.ended() || state.phase().ordinal() < Phase.DELETION.ordinal()) {
			return;
		}

		if (dev.error404.Difficulty.current() == dev.error404.Difficulty.CHILL) {
			return;
		}

		long now = level.getGameTime();
		drainQueue(now, level);

		if (nextFootsteps == 0L) {
			// first cue is never in the first two minutes of a world
			nextFootsteps = now + 20L * 120L;
			nextKnock = now + 20L * (180L + level.random.nextInt(240));
			nextBreath = now + 20L * (90L + level.random.nextInt(180));
		}

		ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));

		if (player.isCreative() || player.isSpectator() || insideAnchor(state, player)) {
			return;
		}

		if (now >= nextFootsteps) {
			nextFootsteps = now + 20L * (300L + level.random.nextInt(420));
			footsteps(level, player);
			return;
		}

		if (now >= nextKnock) {
			nextKnock = now + 20L * (420L + level.random.nextInt(600));
			knock(level, player);
			return;
		}

		if (now >= nextBreath) {
			nextBreath = now + 20L * (240L + level.random.nextInt(360));
			breathe(level, player);
		}
	}

	/** Four steps, closing in, then silence. Staged over 20 ticks so it reads as walking. */
	private static void footsteps(ServerLevel level, ServerPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		double behindX = -Math.sin(yaw);
		double behindZ = Math.cos(yaw);
		long now = level.getGameTime();

		for (int step = 0; step < 4; step++) {
			double distance = 11.0 - step * 2.0;
			double x = player.getX() + behindX * distance;
			double z = player.getZ() + behindZ * distance;

			// five ticks per step: a walk, not four identical clicks in one frame
			QUEUE.addLast(new Cue(level, x, player.getY(), z, SoundEvents.GRASS_STEP,
					SoundSource.AMBIENT, 0.85f, 0.85f + step * 0.035f, (int) now + step * 5));
		}
	}

	/** Plays whatever is due. Called once per dimension per tick. */
	private static void drainQueue(long now, ServerLevel level) {
		if (QUEUE.isEmpty()) {
			return;
		}

		java.util.Iterator<Cue> iterator = QUEUE.iterator();

		while (iterator.hasNext()) {
			Cue cue = iterator.next();

			if (cue.level() != level || now < cue.atTick()) {
				continue;
			}

			level.playSound(null, cue.x(), cue.y(), cue.z(), cue.sound(), cue.source(),
					cue.volume(), cue.pitch());
			iterator.remove();
		}
	}

	/** Three knocks, from a door the player is not looking at. */
	private static void knock(ServerLevel level, ServerPlayer player) {
		BlockPos playerPos = player.blockPosition();
		double yaw = Math.toRadians(player.getYRot());
		double lookX = -Math.sin(yaw);
		double lookZ = Math.cos(yaw);
		BlockPos best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-20, -6, -20), playerPos.offset(20, 6, 20))) {
			var state = level.getBlockState(pos);

			if (!state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS) && !state.is(Blocks.IRON_DOOR)) {
				continue;
			}

			double dx = pos.getX() - player.getX();
			double dz = pos.getZ() - player.getZ();
			double distance = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));

			// behind the player scores high; in front scores low
			double score = -(dx * lookX + dz * lookZ) / distance - distance * 0.01;

			if (score > bestScore) {
				bestScore = score;
				best = pos.immutable();
			}
		}

		if (best == null) {
			return;
		}

		long now = level.getGameTime();

		for (int i = 0; i < 3; i++) {
			QUEUE.addLast(new Cue(level, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5,
					SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0f, 0.72f, (int) now + i * 7));
		}
	}

	/** The player's own breathing, louder than it should be. */
	private static void breathe(ServerLevel level, ServerPlayer player) {
		BlockPos pos = player.blockPosition();

		// only when they have stopped moving: breathing while walking is just a footstep
		if (lastPos == null || player.blockPosition().distSqr(lastPos) > 4.0) {
			lastPos = pos;
			stillTicks = 0;
			return;
		}

		stillTicks++;

		if (stillTicks < 20 * 6) {
			return;
		}

		stillTicks = 0;
		level.playSound(null, player.getX(), player.getEyeY() - 0.2, player.getZ(),
				SoundEvents.AMBIENT_UNDERWATER_LOOP, SoundSource.AMBIENT, 0.35f, 1.6f);
	}

	private static boolean insideAnchor(IntegrityState state, ServerPlayer player) {
		for (BlockPos anchor : state.anchorPositions(player.level().dimension())) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}
}
