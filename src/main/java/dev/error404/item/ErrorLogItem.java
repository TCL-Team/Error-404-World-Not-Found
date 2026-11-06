package dev.error404.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The player's only "UI". Right-click dumps the world's diagnostics into chat as
 * terminal-style lines - deliberately diegetic, no GUI screen.
 */
public class ErrorLogItem extends Item {
	public ErrorLogItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		IntegrityState state = IntegrityState.get(level.getServer());

		level.playSound(null, player.blockPosition(), ModSounds.DATA_CHIRP, SoundSource.PLAYERS, 0.6f, 1.0f);
		((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
				player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.5, 0.4, 0.02);

		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			for (Component line : buildReport(state)) {
				serverPlayer.sendSystemMessage(line);
			}
		}

		return InteractionResult.CONSUME;
	}

	private static List<Component> buildReport(IntegrityState state) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("$ error404 --report").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal("ERR_404: WORLD NOT FOUND").withStyle(ChatFormatting.WHITE));
		lines.add(Component.literal("  phase .......... " + state.phase().name()).withStyle(ChatFormatting.RED));
		lines.add(Component.literal("  integrity ...... " + state.integrity() + "%").withStyle(ChatFormatting.RED));
		lines.add(Component.literal("  fragments ...... " + state.dataFragments() + "/8").withStyle(ChatFormatting.AQUA));
		lines.add(Component.literal("  uptime ......... " + (state.ageTicks() / 20L) + "s").withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal("  session seed ... " + Long.toHexString(state.sessionSeed())).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal("  \"" + state.phase().whisper() + "\"").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}
}
