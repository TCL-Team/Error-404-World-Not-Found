package dev.error404.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.error404.ModSounds;

/**
 * Residue left behind by the deletion front: a black, silent block with a faint grid.
 * Walking on it makes no sound at all - the only block in the game that does not.
 */
public class VoidStoneBlock extends Block {
	public VoidStoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		// Deliberately silent: vanilla step sounds are suppressed here.
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(180) == 0) {
			level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
		}

		if (random.nextInt(2400) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.WHISPER, SoundSource.AMBIENT, 0.35f, 0.6f, false);
		}
	}
}
