package dev.error404.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A torch that burns "cold": it lights the world for the player, but 422 reads it as
 * darkness. Placing a few of these is the low-tech way to make a room feel safe.
 */
public class NullTorchBlock extends Block {
	/** 1.21.11 removed {@code DirectionProperty}; horizontal facing is a plain EnumProperty. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);

	public NullTorchBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.75;
		double z = pos.getZ() + 0.5;
		level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.1, y, z + (random.nextDouble() - 0.5) * 0.1, 0, 0.002, 0);

		if (random.nextInt(22) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x, y + 0.15, z, 0, 0.01, 0);
		}
	}
}
