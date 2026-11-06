package dev.error404.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;

/**
 * A recovered piece of the world's save data. Using one gives the world back 6% integrity
 * and counts towards the "World Found" ending (8 fragments). The count lives in the
 * world's {@link IntegrityState}, so co-op players share progress.
 */
public class DataFragmentItem extends Item {
	public static final int INTEGRITY_PER_USE = 6;

	public DataFragmentItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		IntegrityState state = IntegrityState.get(level.getServer());

		state.restore(INTEGRITY_PER_USE);
		state.addFragment();

		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			dev.error404.Achievements.grant(serverPlayer, dev.error404.Achievements.FIRST_FRAGMENT);
		}

		level.playSound(null, player.blockPosition(), ModSounds.FRAGMENT_PICKUP, SoundSource.PLAYERS, 0.9f, 1.0f);
		player.displayClientMessage(
				Component.literal("ERR_404: fragment committed - integrity " + state.integrity() + "% (" + state.dataFragments() + "/8)")
						.withStyle(ChatFormatting.AQUA),
				true
		);

		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResult.CONSUME;
	}
}
