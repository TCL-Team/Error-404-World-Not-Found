package dev.error404.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * 422 — THE UNPROCESSABLE.
 *
 * <p>Deliberately not a normal mob: it has no goals, no pathfinding and no attack loop.
 * Behaviour is driven by {@link dev.error404.entity.Mode} and the phase of the world:</p>
 *
 * <ul>
 *   <li><b>OBSERVE</b> (P1+): stands still, teleports out of the player's view when unobserved.</li>
 *   <li><b>APPROACH</b> (P2+): closes the distance while off-screen, never in a straight line.</li>
 *   <li><b>PRESENCE</b>: stops ~5 blocks away, drains the world around it.</li>
 *   <li><b>HUNT</b> (P4): walks at the player and deletes light/blocks it touches.</li>
 *   <li><b>DORMANT</b>: wanders, harmless, found standing behind the player's base.</li>
 * </ul>
 *
 * <p>The signature rule only exists in ANNEXATION: looking at it for more than
 * {@link #STARE_LIMIT_TICKS} deals damage. Before that phase, staring is free.</p>
 */
public class Error422Entity extends Mob {
	public static final int STARE_LIMIT_TICKS = 60; // 3 seconds
	private static final EntityDataAccessor<Integer> DATA_MODE =
			SynchedEntityData.defineId(Error422Entity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TEXTURE =
			SynchedEntityData.defineId(Error422Entity.class, EntityDataSerializers.INT);

	public enum Mode {
		DORMANT, OBSERVE, APPROACH, PRESENCE, HUNT
	}

	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState glitchAnimationState = new AnimationState();

	private int stareTicks;
	private int abilityCooldown;
	private int despawnTimer = 20 * 90;
	/** Ticks until it may hit on contact again. */
	private int contactCooldown;
	/** How many player deaths this session has fed it. */
	private int feasts;
	/** Last known "you went there" position, set by items that move the player. */
	private BlockPos stalkTarget;
	private boolean dormant;

	public Error422Entity(EntityType<? extends Error422Entity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1024.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, Mode.OBSERVE.ordinal());
		builder.define(DATA_TEXTURE, 0);
	}

	public Mode mode() {
		return Mode.values()[Mth.clamp(entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
	}

	public void setMode(Mode mode) {
		entityData.set(DATA_MODE, mode.ordinal());
	}

	/** 0 = base (checker + error text), 1 = missing texture, 2 = the face variant. */
	public int textureVariant() {
		return entityData.get(DATA_TEXTURE);
	}

	public void setTextureVariant(int variant) {
		entityData.set(DATA_TEXTURE, Mth.clamp(variant, 0, 2));
	}

	public boolean isDormant() {
		return dormant;
	}

	public int stareTicks() {
		return stareTicks;
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	public void tick() {
		super.tick();

		if (level().isClientSide()) {
			if (tickCount % 20 == 0) {
				idleAnimationState.startIfStopped(tickCount);
			}

			if (textureVariant() == 1 && tickCount % 3 == 0) {
				glitchAnimationState.startIfStopped(tickCount);
			}

			return;
		}

		if (isRemoved()) {
			return;
		}

		Phase phase = currentPhase();
		updateMode(phase);
		updateStare(phase);

		if (abilityCooldown > 0) {
			abilityCooldown--;
		}

		if (contactCooldown > 0) {
			contactCooldown--;
		}

		if (stalkTarget != null) {
			getNavigation().moveTo(stalkTarget.getX() + 0.5, stalkTarget.getY(), stalkTarget.getZ() + 0.5, 1.3);

			if (blockPosition().distSqr(stalkTarget) < 9.0) {
				stalkTarget = null;
			}
		}

		// ANNEXATION aura: darkness follows it. The player has to hold a light source, not
		// just stand still and out-heal it.
		// It follows you into sleep. From CASCADE on, sleeping near it is not safe: the bed
		// throws you out and it is standing there. This is the single nastiest thing it does,
		// so it is gated on the world already being mostly gone.
		if (phase.ordinal() >= dev.error404.Difficulty.current().sleepHauntFromPhase && tickCount % 40 == 0) {
			hauntSleepers(phase);
		}

		if (dev.error404.Difficulty.current().darknessAura && phase == Phase.ANNEXATION && tickCount % 60 == 0) {
			Player near = nearestPlayer();

			if (near != null && distanceTo(near) < 20.0 && near instanceof net.minecraft.server.level.ServerPlayer sp) {
				sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.DARKNESS, 120, 0));
			}
		}

		switch (mode()) {
			case OBSERVE -> tickObserve(phase);
			case APPROACH -> tickApproach();
			case PRESENCE -> tickPresence();
			case HUNT -> tickHunt();
			case DORMANT -> tickDormant();
		}

		if (mode() != Mode.DORMANT && --despawnTimer <= 0) {
			vanish();
		}
	}

	private Phase currentPhase() {
		if (level().getServer() == null) {
			return Phase.SIGNAL;
		}

		return IntegrityState.get(level().getServer()).phase();
	}

	private void updateMode(Phase phase) {
		if (dormant) {
			setMode(Mode.DORMANT);
			return;
		}

		setMode(switch (phase) {
			case SIGNAL -> Mode.OBSERVE;
			case DESYNC, DELETION -> nearestPlayerDistance() < 14.0 ? Mode.PRESENCE : Mode.APPROACH;
			case CASCADE -> nearestPlayerDistance() < 10.0 ? Mode.PRESENCE : Mode.APPROACH;
			case ANNEXATION -> Mode.HUNT;
		});
	}

	// ------------------------------------------------------------------ behaviour

	private void tickObserve(Phase phase) {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		getLookControl().setLookAt(player, 90f, 90f);

		// It refuses to move while it is being watched. Look away and it is somewhere else.
		if (!isObserved(player) && abilityCooldown == 0 && random.nextInt(160) == 0) {
			teleportToRing(player, 22 + random.nextInt(8));
			playData(0.7f);
			abilityCooldown = 40;
		}
	}

	private void tickApproach() {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		// CASCADE and later: it stops chasing you and starts walking your own route from a
		// minute ago. You will recognise the movement before you recognise the thing doing it.
		if (currentPhase().ordinal() >= Phase.CASCADE.ordinal()
				&& player instanceof ServerPlayer serverPlayer) {
			Vec3 memory = dev.error404.haunt.PathMemory.at(serverPlayer,
					Math.max(1, 60 - currentPhase().ordinal() * 12));

			if (memory != null && memory.distanceTo(position()) > 3.0) {
				getNavigation().moveTo(memory.x, memory.y, memory.z, 1.05);
				face(player);
				return;
			}
		}

		if (!isObserved(player) && abilityCooldown == 0) {
			double distance = distanceTo(player);

			if (distance > 13.0) {
				teleportToRing(player, Math.max(9.0, distance - 7.0));
				playData(0.85f);
				abilityCooldown = 30;
			}
		}

		// It does not aim at you. It aims at where you will be in two seconds, which is why
		// turning a corner does not shake it and why running in a straight line always fails.
		if (player instanceof ServerPlayer serverPlayer && distanceTo(player) > 6.0) {
			BlockPos predicted = dev.error404.haunt.PathMemory.predict(serverPlayer);
			getNavigation().moveTo(predicted.getX() + 0.5, predicted.getY(), predicted.getZ() + 0.5, 1.2);
			face(player);
		}

		getLookControl().setLookAt(player, 90f, 90f);
	}

	private void tickPresence() {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		setTarget(player);
		setDeltaMovement(Vec3.ZERO);
		double distance = distanceTo(player);

		// Close enough to touch. In ANNEXATION it does.
		if (distance < 2.8) {
			contactAttack(player);
		}

		if (tickCount % 40 == 0 && distance < 9.0) {
			level().playSound(null, blockPosition(), ModSounds.HEARTBEAT, SoundSource.AMBIENT, 0.9f, 0.85f);
		}

		if (tickCount % 100 == 0 && distance < 10.0) {
			drainWorldAround(player);
		}

		// Every few seconds of standing next to you it takes a step closer, without moving.
		if (currentPhase().ordinal() >= Phase.CASCADE.ordinal() && distance > 2.6 && tickCount % 60 == 0) {
			Vec3 toward = player.position().subtract(position()).normalize().scale(0.7);
			teleportTo(getX() + toward.x, getY(), getZ() + toward.z);
			level().playSound(null, blockPosition(), ModSounds.STATIC_BURST, SoundSource.HOSTILE, 0.8f, 0.6f);
			dev.error404.event.ServerEvents.flashGlitch(level().getServer(), "ERR_422");
		}
	}

	private void tickHunt() {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		setTarget(player);

		if (playerSafeFromUs(player)) {
			// It paces the edge of the zone. It does not leave.
			getNavigation().stop();
			face(player);

			if (tickCount % 100 == 0) {
				level().playSound(null, blockPosition(), ModSounds.ERROR_BLIP, SoundSource.HOSTILE, 1.0f, 0.5f);
			}

			return;
		}

		getNavigation().moveTo(player, 1.35);
		double distance = distanceTo(player);

		// Reach. If it is within arm's length, it takes a piece.
		if (distance < 3.0) {
			contactAttack(player);
		}

		if (tickCount % 10 == 0) {
			deleteLightAround();
		}

		// Doors are a suggestion. If it cannot reach you, it makes a hole.
		int breakInterval = dev.error404.Difficulty.current().breakIntervalTicks;

		if (breakInterval > 0 && tickCount % breakInterval == 0 && distance < 24.0 && !hasLineOfSightTo(player)) {
			breakTowards(player);
		}

		// The stare rule: looking at a hunting 422 is the only thing that hurts it back.
		if (distance < 24.0 && isObserved(player)) {
			return;
		}

		// If you cannot see it, it is already somewhere else - always closer.
		if (abilityCooldown == 0 && random.nextInt(Math.max(1, dev.error404.Difficulty.current().teleportIntervalTicks)) == 0) {
			double step = distance > 12.0 ? 10.0 : (distance > 6.0 ? 5.0 : 2.5);
			teleportToRing(player, Math.max(2.2, distance - step));
			playData(1.0f);
			dev.error404.event.ServerEvents.flashGlitch(level().getServer(), "ERR_422");
			abilityCooldown = 50;
		}
	}

	private void tickDormant() {
		// Harmless. It just stands there, exactly where the player's base is not facing.
		if (random.nextInt(400) == 0) {
			level().playSound(null, blockPosition(), ModSounds.ENTITY_AMBIENT, SoundSource.AMBIENT, 0.25f, 0.6f);
		}
	}

	// ------------------------------------------------------------------ the look rule

	private void updateStare(Phase phase) {
		Player player = nearestPlayer();

		if (player == null) {
			stareTicks = 0;
			return;
		}

		boolean watched = isObserved(player);
		setTextureVariant(watched && distanceTo(player) < 12.0 && phase == Phase.ANNEXATION ? 2 : (random.nextInt(200) == 0 ? 1 : 0));

		if (!watched) {
			stareTicks = Math.max(0, stareTicks - 2);
			return;
		}

		// Signature rule, only in the final phase. Before that, staring is just... staring.
		if (phase != Phase.ANNEXATION) {
			stareTicks = 0;
			return;
		}

		stareTicks++;

		if (stareTicks == STARE_LIMIT_TICKS / 2) {
			level().playSound(null, blockPosition(), ModSounds.WHISPER, SoundSource.HOSTILE, 1.0f, 0.7f);
		}

		// Staring does not just hurt you - it tells it exactly where you are.
		if (stareTicks == STARE_LIMIT_TICKS / 3 && distanceTo(player) < 40.0) {
			teleportToRing(player, Math.max(2.4, distanceTo(player) - 6.0));
			face(player);
			level().playSound(null, blockPosition(), ModSounds.PHASE_SHIFT, SoundSource.HOSTILE, 1.0f, 0.4f);
		}

		if (stareTicks >= STARE_LIMIT_TICKS) {
			// Three completed stares and the world answers with the 0x00 ending.
			if (stareTicks == STARE_LIMIT_TICKS && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				dev.error404.ending.EndingManager.registerStare(serverPlayer);
			}

			if (tickCount % 20 == 0) {
				punish(player);
			}
		}
	}

	/**
	 * The stare rule, made real. Holding eye contact costs health every second and the
	 * damage ramps: three seconds of staring is survivable, six is not.
	 */
	private void punish(Player player) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		dev.error404.Difficulty difficulty = dev.error404.Difficulty.current();
		int stage = Math.min(4, stareTicks / STARE_LIMIT_TICKS);
		float damage = difficulty.stareBaseDamage + stage * difficulty.stareDamageStep;

		if (damage <= 0.0f) {
			return;
		}

		player.hurtServer(serverLevel, Damage.unprocessable(level(), this), damage);
		player.displayClientMessage(net.minecraft.network.chat.Component
				.literal("ERR_422: unprocessable entity in range")
				.withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
		level().playSound(null, blockPosition(), ModSounds.ENTITY_ATTACK, SoundSource.HOSTILE, 1.4f, 0.85f);
		dev.error404.event.ServerEvents.flashGlitch(level().getServer(), "ERR_422");

		// The screen stops working for a second: blindness gives the player a reason to
		// break eye contact instead of just eating the damage.
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.NAUSEA, 100, 0));

		serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.2, getZ(), 40, 0.6, 0.8, 0.6, 0.03);

		IntegrityState.get(level().getServer()).drain(2);
	}

	/**
	 * Wakes a sleeping player inside its radius by destroying the bed under them. In
	 * ANNEXATION it also teleports right next to the bed, so the first thing the player sees
	 * when the screen fades back in is it, three blocks away, facing them.
	 */
	private void hauntSleepers(Phase phase) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		for (ServerPlayer sleeper : serverLevel.players()) {
			if (!sleeper.isSleeping()) {
				continue;
			}

			double distance = distanceTo(sleeper);

			if (distance > 32.0) {
				continue;
			}

			BlockPos bed = sleeper.getSleepingPos().orElse(null);

			if (bed == null) {
				continue;
			}

			sleeper.stopSleeping();

			if (phase == Phase.ANNEXATION) {
				teleportToRing(sleeper, 3.0);
				face(sleeper);
			}

			sleeper.displayClientMessage(net.minecraft.network.chat.Component
					.literal("ERR_422: you were not asleep. it was.")
					.withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
			serverLevel.playSound(null, bed, ModSounds.ENTITY_ATTACK, SoundSource.HOSTILE, 1.6f, 0.6f);
			serverLevel.playSound(null, bed, ModSounds.WHISPER, SoundSource.AMBIENT, 1.2f, 0.5f);
			dev.error404.event.ServerEvents.flashGlitch(serverLevel.getServer(), "ERR_422");
			IntegrityState.get(level().getServer()).drain(4);

			// The bed itself does not survive this.
			if (dev.error404.Error404Config.get().realBlockDeletion) {
				BlockState bedState = serverLevel.getBlockState(bed);

				if (bedState.is(net.minecraft.tags.BlockTags.BEDS)) {
					dev.error404.deletion.DeletionJournal.record(bed.getX() >> 4, bed.getZ() >> 4, bed, bedState);
					serverLevel.destroyBlock(bed, false, this, 512);
				}
			}

			return;
		}
	}

	/** True while the player stands inside an anchor zone (and the anchor still exists). */
	private boolean playerSafeFromUs(Player player) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		for (BlockPos anchor : IntegrityState.get(serverLevel.getServer()).anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}

	/** Straight line from its head to the player's eye, checked against the world. */
	private boolean hasLineOfSightTo(Player player) {
		return level().clip(new net.minecraft.world.level.ClipContext(
				new Vec3(getX(), getY() + getBbHeight() * 0.9, getZ()),
				player.getEyePosition(),
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
				== net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/**
	 * Removes what is in the way, one block at a time, from head height down. Cheap (four
	 * blocks a second), permanent (it is not put back), and it means a player cannot simply
	 * shut a door and wait.
	 */
	private void breakTowards(Player player) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!dev.error404.Error404Config.get().realBlockDeletion) {
			level().playSound(null, blockPosition(), ModSounds.CHUNK_DELETE, SoundSource.HOSTILE, 0.6f, 1.1f);
			return;
		}

		Vec3 dir = new Vec3(player.getX() - getX(), 0.0, player.getZ() - getZ()).normalize();

		for (int step = 1; step <= 2; step++) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos pos = BlockPos.containing(getX() + dir.x * step, getY() + dy, getZ() + dir.z * step);
				BlockState state = serverLevel.getBlockState(pos);

				if (state.isAir() || state.hasBlockEntity()
						|| dev.error404.deletion.DeletionRules.isProtected(serverLevel, pos)) {
					continue;
				}

				// Every break is journaled, so /error404 restore still undoes it.
				dev.error404.deletion.DeletionJournal.record(pos.getX() >> 4, pos.getZ() >> 4, pos, state);
				serverLevel.destroyBlock(pos, false, this, 512);
				serverLevel.playSound(null, pos, ModSounds.STATIC_BURST, SoundSource.HOSTILE, 0.9f, 0.7f);
			}
		}
	}

	/**
	 * Touch damage. Not a melee swing - it happens on contact, silently, and it is enough to
	 * kill a player who stands still next to it.
	 */
	private void contactAttack(Player player) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// One hit per second, not one per tick: without this it deletes a full-health player
		// in under a second and there is no game left to play.
		if (contactCooldown > 0) {
			return;
		}

		contactCooldown = 20;

		float contact = dev.error404.Difficulty.current().contactDamage;

		if (contact <= 0.0f) {
			return;
		}

		player.hurtServer(serverLevel, Damage.unprocessable(level(), this), contact);
		player.knockback(0.6, getX() - player.getX(), getZ() - player.getZ());
		player.displayClientMessage(net.minecraft.network.chat.Component
				.literal("ERR_422: you are in the way")
				.withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
		level().playSound(null, blockPosition(), ModSounds.ENTITY_ATTACK, SoundSource.HOSTILE, 1.5f, 0.7f);
		dev.error404.event.ServerEvents.flashGlitch(level().getServer(), "ERR_422");
		serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.2, player.getZ(),
				24, 0.5, 0.7, 0.5, 0.04);
	}

	/**
	 * It will not cross into an anchor's stability zone. That is what the anchor is *for*:
	 * the one patch of world that has been processed, and it does not fit there. Standing in
	 * one keeps you alive, which makes the ritual the difference between a story and a rage
	 * quit.
	 */
	private boolean inSafeZoneAt(double x, double z) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		for (BlockPos anchor : IntegrityState.get(serverLevel.getServer()).anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, BlockPos.containing(x, anchor.getY(), z))) {
				return true;
			}
		}

		return false;
	}

	private boolean inSafeZone() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		for (BlockPos anchor : IntegrityState.get(serverLevel.getServer()).anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, blockPosition())) {
				return true;
			}
		}

		return false;
	}

	private boolean isObserved(Player player) {
		Vec3 toEntity = position().add(0, getBbHeight() * 0.75, 0).subtract(player.getEyePosition()).normalize();
		double dot = player.getViewVector(1.0f).dot(toEntity);
		return dot > 0.92 && player.hasLineOfSight(this) && distanceTo(player) < 64.0;
	}

	// ------------------------------------------------------------------ world effects

	private void drainWorldAround(Player player) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// Safe mode: it still reaches for the world, but nothing is actually removed.
		if (!dev.error404.Error404Config.get().realBlockDeletion) {
			serverLevel.playSound(null, blockPosition(), ModSounds.CHUNK_DELETE, SoundSource.HOSTILE, 0.6f, 1.3f);
			serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 24, 1.0, 1.2, 1.0, 0.05);
			return;
		}

		for (int i = 0; i < 6; i++) {
			BlockPos pos = BlockPos.containing(
					getX() + (random.nextDouble() - 0.5) * 6,
					getY() + random.nextDouble() * 3 - 0.5,
					getZ() + (random.nextDouble() - 0.5) * 6
			);

			if (pos.distSqr(player.blockPosition()) < 64) {
				continue;
			}

			if (dev.error404.deletion.DeletionRules.isProtected(serverLevel, pos)) {
				continue;
			}

			BlockState state = serverLevel.getBlockState(pos);

			if (!state.isAir() && state.getDestroySpeed(serverLevel, pos) < 50) {
				serverLevel.destroyBlock(pos, false, this, 512);
				serverLevel.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
			}
		}
	}

	private void deleteLightAround() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!dev.error404.Error404Config.get().realBlockDeletion) {
			return;
		}

		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos pos = blockPosition().offset(dx, dy, dz);
					BlockState state = serverLevel.getBlockState(pos);
					String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getKey(state.getBlock()).getPath();

					// Never removes its own null torches or anything a player could rely on
					// for the anchor ritual.
					if (path.contains("torch") || path.contains("lantern") || path.contains("candle")) {
						if (dev.error404.deletion.DeletionRules.isProtected(serverLevel, pos)) {
							continue;
						}

						serverLevel.removeBlock(pos, false);
						serverLevel.playSound(null, pos, ModSounds.STATIC_BURST, SoundSource.BLOCKS, 0.5f, 1.4f);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private Player nearestPlayer() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return level().getNearestPlayer(this, 96.0);
		}

		return serverLevel.getNearestPlayer(this, 96.0);
	}

	private double nearestPlayerDistance() {
		Player player = nearestPlayer();
		return player == null ? Double.MAX_VALUE : distanceTo(player);
	}

	private void teleportToRing(Player player, double radius) {
		double angle = random.nextDouble() * Math.PI * 2;
		double x = player.getX() + Math.cos(angle) * radius;
		double z = player.getZ() + Math.sin(angle) * radius;

		// Never materialise inside an anchor's zone: that is the player's one safe place.
		int guard = 0;

		while (inSafeZoneAt(x, z) && guard++ < 8) {
			angle += Math.PI / 3.0;
			x = player.getX() + Math.cos(angle) * radius;
			z = player.getZ() + Math.sin(angle) * radius;
		}
		int y = player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(x, 0, z).getX(), BlockPos.containing(x, 0, z).getZ());

		teleportTo(x, y, z);

		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.4, z, 18, 0.3, 0.8, 0.3, 0.01);
			serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.9, z, 8, 0.2, 0.3, 0.2, 0.01);
		}
	}

	private void playData(float volume) {
		level().playSound(null, blockPosition(), ModSounds.DATA_CHIRP, SoundSource.HOSTILE, volume, 0.55f);
	}

	public void vanish() {
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.4, getZ(), 60, 0.5, 1.0, 0.5, 0.1);
			serverLevel.playSound(null, blockPosition(), ModSounds.STATIC_BURST, SoundSource.HOSTILE, 1.0f, 0.8f);

			// Surviving it is supposed to feel like surviving, not like farming: one shard
			// drops, and only sometimes.
			if (random.nextInt(3) == 0) {
				spawnAtLocation(serverLevel, new net.minecraft.world.item.ItemStack(dev.error404.ModItems.CORRUPT_SHARD, 1));
			}
		}

		discard();
	}

	/**
	 * Called by the anomaly manager when the session has gone quiet: it is not gone, it was
	 * just standing somewhere you could not see. Places it in the player's blind spot, 20-28
	 * blocks out, and does *not* let it teleport into an anchor zone.
	 */
	public void pressIn(Player player) {
		teleportToRing(player, 20.0 + random.nextInt(8));
		face(player);
		level().playSound(null, player.blockPosition(), ModSounds.WHISPER, SoundSource.AMBIENT, 0.5f, 0.6f);
	}

	/** Used by the void pearl: it saw where you went and it will walk there. */
	public void setStalkTarget(BlockPos pos) {
		this.stalkTarget = pos.immutable();
	}

	public void setDormant(boolean dormant) {
		this.dormant = dormant;
		this.despawnTimer = Integer.MAX_VALUE;
		setPersistenceRequired();
		setMode(dormant ? Mode.DORMANT : Mode.OBSERVE);
	}

	// ------------------------------------------------------------------ misc overrides

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Immune. It flinches, then forgets you exist.
		if (random.nextInt(3) == 0) {
			level.playSound(null, blockPosition(), ModSounds.ERROR_BLIP, SoundSource.HOSTILE, 1.0f, 0.6f);
		}

		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return true;
	}

	/**
	 * Every player death in the session feeds it: it moves faster, and it stops pretending to
	 * be a silhouette. The modifier is capped so a wipe does not make it unkillable-adjacent.
	 */
	public void feedOnDeath() {
		feasts++;

		var speed = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			speed.setBaseValue(Math.min(0.42, 0.26 + feasts * 0.03));
		}

		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(),
					60, 0.4, 1.2, 0.4, 0.05);
		}
	}

	public int feasts() {
		return feasts;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		level().playSound(null, pos, ModSounds.FOOTSTEP_ECHO, SoundSource.HOSTILE, 0.5f, 0.6f);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	/** Facing the player is the whole point, so it snaps instead of easing. */
	private void face(Player player) {
		double dx = player.getX() - getX();
		double dz = player.getZ() - getZ();
		double dy = player.getEyeY() - (getY() + getBbHeight() * 0.9);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		setYRot(yaw);
		yHeadRot = yaw;
		setXRot(Mth.clamp(pitch, -80.0f, 80.0f));
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(target);

		if (target instanceof Player player) {
			face(player);
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return ModSounds.ENTITY_AMBIENT;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", dormant);
		output.putInt("despawn_timer", despawnTimer);
		output.putInt("feasts", feasts);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		dormant = input.getBooleanOr("dormant", false);
		despawnTimer = input.getIntOr("despawn_timer", 20 * 90);
		feasts = input.getIntOr("feasts", 0);
	}
}
