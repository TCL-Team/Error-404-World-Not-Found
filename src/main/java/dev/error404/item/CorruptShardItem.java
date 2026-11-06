package dev.error404.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;

import java.util.function.Consumer;

/**
 * Dropped by 422 when it leaves. Four of these craft an Anchor Core, which is the only
 * reason a player would ever choose to look at the thing.
 */
public class CorruptShardItem extends Item {
	public CorruptShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		// Deliberate double-edged use: costs integrity, but tells the player where 422 is.
		IntegrityState state = IntegrityState.get(level.getServer());
		state.drain(2);
		player.hurtServer(level instanceof net.minecraft.server.level.ServerLevel serverLevel ? serverLevel : null,
				level.damageSources().magic(), 1.0f);
		level.playSound(null, player.blockPosition(), ModSounds.STATIC_BURST, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.displayClientMessage(
				Component.literal("ERR_422: it knows you held this.").withStyle(ChatFormatting.DARK_RED),
				true
		);

		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, adder, flag);
		adder.accept(Component.literal("a piece of a world that failed to load").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		adder.accept(Component.literal("using it costs 2% integrity").withStyle(ChatFormatting.RED));
	}
}
