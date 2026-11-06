package dev.error404.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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

		if (!isObserved(player) && abilityCooldown == 0) {
			double distance = distanceTo(player);

			if (distance > 13.0) {
				teleportToRing(player, Math.max(9.0, distance - 7.0));
				playData(0.85f);
				abilityCooldown = 30;
			}
		}

		getLookControl().setLookAt(player, 90f, 90f);
	}

	private void tickPresence() {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		getLookControl().setLookAt(player, 60f, 60f);
		setDeltaMovement(Vec3.ZERO);

		if (tickCount % 40 == 0 && distanceTo(player) < 9.0) {
			level().playSound(null, blockPosition(), ModSounds.HEARTBEAT, SoundSource.AMBIENT, 0.7f, 0.85f);
		}

		if (tickCount % 100 == 0 && distanceTo(player) < 10.0) {
			drainWorldAround(player);
		}
	}

	private void tickHunt() {
		Player player = nearestPlayer();
		if (player == null) {
			return;
		}

		getLookControl().setLookAt(player, 90f, 90f);
		getNavigation().moveTo(player, 1.15);

		if (tickCount % 10 == 0) {
			deleteLightAround();
		}

		if (abilityCooldown == 0 && random.nextInt(90) == 0 && distanceTo(player) > 16.0) {
			teleportToRing(player, 8 + random.nextInt(4));
			playData(1.0f);
			abilityCooldown = 60;
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

		if (stareTicks >= STARE_LIMIT_TICKS && tickCount % 20 == 0) {
			punish(player);
		}
	}

	private void punish(Player player) {
		player.hurtServer((ServerLevel) level(), damageSources().magic(), 2.0f);
		player.displayClientMessage(net.minecraft.network.chat.Component
				.literal("ERR_422: unprocessable entity in range")
				.withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
		level().playSound(null, blockPosition(), ModSounds.ENTITY_ATTACK, SoundSource.HOSTILE, 1.2f, 0.9f);
		dev.error404.event.ServerEvents.flashGlitch(level().getServer(), "ERR_422");

		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.2, getZ(), 30, 0.5, 0.6, 0.5, 0.02);
		}

		if (IntegrityState.get(level().getServer()) != null) {
			IntegrityState.get(level().getServer()).drain(1);
		}
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

		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos pos = blockPosition().offset(dx, dy, dz);
					BlockState state = serverLevel.getBlockState(pos);
					String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getKey(state.getBlock()).getPath();

					if (path.contains("torch") || path.contains("lantern") || path.contains("candle")) {
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

			// Leaving behind a shard is the only reward the player gets for surviving it.
			spawnAtLocation(serverLevel, new net.minecraft.world.item.ItemStack(dev.error404.ModItems.CORRUPT_SHARD, 1 + random.nextInt(2)));
		}

		discard();
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

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return ModSounds.ENTITY_AMBIENT;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", dormant);
		output.putInt("despawn_timer", despawnTimer);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		dormant = input.getBooleanOr("dormant", false);
		despawnTimer = input.getIntOr("despawn_timer", 20 * 90);
	}
}
