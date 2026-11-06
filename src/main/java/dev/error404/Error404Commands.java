package dev.error404;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import dev.error404.integrity.Error404Director;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * {@code /error404 ...} - the whole debug surface of the mod.
 *
 * <p>Every destructive feature must be reversible from here. If a player is stuck in a
 * broken world, {@code /error404 stop} and {@code /error404 restore} are the escape hatch.</p>
 */
public final class Error404Commands {
	private Error404Commands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("error404")
				.requires(Error404Commands::isOperator)
				.executes(Error404Commands::status)
				.then(Commands.literal("help").executes(Error404Commands::help))
				.then(Commands.literal("status").executes(Error404Commands::status))
				.then(Commands.literal("start").executes(Error404Commands::start))
				.then(Commands.literal("stop").executes(Error404Commands::stop))
				.then(Commands.literal("restore").executes(Error404Commands::restore))
				.then(Commands.literal("demo").executes(Error404Commands::demo))
				.then(Commands.literal("ending")
						.then(Commands.argument("ending", StringArgumentType.word())
								.suggests((context, builder) -> {
									for (dev.error404.ending.EndingType type : dev.error404.ending.EndingType.values()) {
										builder.suggest(type.name().toLowerCase(Locale.ROOT));
									}

									return builder.buildFuture();
								})
								.executes(Error404Commands::forceEnding)))
				.then(Commands.literal("reload").executes(Error404Commands::reload))
				.then(Commands.literal("phase")
						.then(Commands.argument("phase", StringArgumentType.word())
								.suggests((context, builder) -> {
									for (Phase phase : Phase.values()) {
										builder.suggest(phase.name().toLowerCase(Locale.ROOT));
									}

									return builder.buildFuture();
								})
								.executes(Error404Commands::setPhase)))
				.then(Commands.literal("setintegrity")
						.then(Commands.argument("value", IntegerArgumentType.integer(0, IntegrityState.MAX_INTEGRITY))
								.executes(Error404Commands::setIntegrity)))
				.then(Commands.literal("config")
						.then(Commands.literal("reload").executes(Error404Commands::reload))
						.then(Commands.literal("save").executes(Error404Commands::saveConfig))));
	}

	private static boolean isOperator(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		IntegrityState state = IntegrityState.get(server);

		reply(source, "ERROR 404: WORLD NOT FOUND", ChatFormatting.WHITE);
		reply(source, " enabled: " + Error404Config.get().enabled + "   active: " + state.active() + "   ended: " + state.ended(), ChatFormatting.GRAY);
		reply(source, " integrity: " + state.integrity() + "%   phase: " + state.phase().name() + "   fragments: " + state.dataFragments() + "/8", ChatFormatting.RED);
		reply(source, " age: " + (state.ageTicks() / 20L) + "s   session seed: " + state.sessionSeed(), ChatFormatting.DARK_GRAY);
		reply(source, " \"" + state.phase().whisper() + "\"", ChatFormatting.DARK_GRAY);
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		reply(source, "/error404 status - integrity, phase, seed", ChatFormatting.GRAY);
		reply(source, "/error404 start|stop - begin or end a session", ChatFormatting.GRAY);
		reply(source, "/error404 restore - restore integrity and un-end the world", ChatFormatting.GRAY);
		reply(source, "/error404 phase <signal|desync|deletion|cascade|annexation>", ChatFormatting.GRAY);
		reply(source, "/error404 setintegrity <0-100>", ChatFormatting.GRAY);
		reply(source, "/error404 demo - 15x faster pacing for testing", ChatFormatting.GRAY);
		reply(source, "/error404 reload - reload config/error404.json", ChatFormatting.GRAY);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		IntegrityState state = IntegrityState.get(server);
		state.start(server.overworld().getSeed() * 31L ^ System.nanoTime(), IntegrityState.MAX_INTEGRITY);
		reply(context.getSource(), "session started (integrity 100%, phase SIGNAL, seed " + state.sessionSeed() + ")", ChatFormatting.GREEN);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> context) {
		IntegrityState state = IntegrityState.get(context.getSource().getServer());
		state.stop();
		reply(context.getSource(), "session stopped - the world will not change until you start it again", ChatFormatting.GREEN);
		return 1;
	}

	private static int restore(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		IntegrityState state = IntegrityState.get(server);

		// The full undo: integrity back to 100, the ended flag cleared, every chunk the front
		// touched regenerated from the seed, and the anomaly bookkeeping wiped.
		int chunks = dev.error404.deletion.DeletionFront.restore(server);
		state.reopen();
		state.setIntegrity(IntegrityState.MAX_INTEGRITY);
		state.stop();
		dev.error404.ending.EndingManager.resetStareCount();
		dev.error404.event.ServerEvents.pushState(server);

		reply(context.getSource(), "integrity restored to 100%", ChatFormatting.GREEN);
		reply(context.getSource(), chunks + " deleted chunk(s) queued for regeneration on next visit", ChatFormatting.GRAY);
		reply(context.getSource(), "the world is no longer marked as ended - /error404 start begins a fresh session", ChatFormatting.GRAY);
		return 1;
	}

	private static int demo(CommandContext<CommandSourceStack> context) {
		Error404Director.setDemoMultiplier(15);
		MinecraftServer server = context.getSource().getServer();
		IntegrityState state = IntegrityState.get(server);
		state.start(server.overworld().getSeed() * 31L ^ System.nanoTime(), IntegrityState.MAX_INTEGRITY);
		reply(context.getSource(), "demo pacing armed (15x) - a full 100% -> 0% run takes about 5 minutes", ChatFormatting.YELLOW);
		return 1;
	}

	private static int setPhase(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "phase");
		Phase phase = Phase.byName(name);
		if (phase == null) {
			reply(context.getSource(), "unknown phase \"" + name + "\"", ChatFormatting.RED);
			return 0;
		}

		IntegrityState state = IntegrityState.get(context.getSource().getServer());
		state.setIntegrity(IntegrityState.MAX_INTEGRITY - (phase.ordinal() + 1) * 20 + 10);
		state.forcePhase(phase);
		reply(context.getSource(), "phase forced to " + phase.name(), ChatFormatting.YELLOW);
		return 1;
	}

	private static int forceEnding(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "ending");
		dev.error404.ending.EndingType type = dev.error404.ending.EndingType.byName(name);

		if (type == null) {
			reply(context.getSource(), "unknown ending \"" + name + "\" (salvage, delete, null)", ChatFormatting.RED);
			return 0;
		}

		MinecraftServer server = context.getSource().getServer();
		IntegrityState state = IntegrityState.get(server);

		if (state.ended()) {
			reply(context.getSource(), "this world already ended (" + dev.error404.ending.EndingType.byCode(state.ending()).title()
					+ ") - run /error404 restore first", ChatFormatting.RED);
			return 0;
		}

		dev.error404.ending.EndingManager.force(server, type);
		reply(context.getSource(), "ending triggered: " + type.title(), ChatFormatting.YELLOW);
		return 1;
	}

	private static int setIntegrity(CommandContext<CommandSourceStack> context) {
		int value = IntegerArgumentType.getInteger(context, "value");
		IntegrityState state = IntegrityState.get(context.getSource().getServer());
		state.setIntegrity(value);
		reply(context.getSource(), "integrity set to " + state.integrity() + "%", ChatFormatting.YELLOW);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		Error404Config.load();
		reply(context.getSource(), "config reloaded from config/error404.json", ChatFormatting.GREEN);
		return 1;
	}

	private static int saveConfig(CommandContext<CommandSourceStack> context) {
		Error404Config.save();
		reply(context.getSource(), "config saved", ChatFormatting.GREEN);
		return 1;
	}

	private static void reply(CommandSourceStack source, String line, ChatFormatting color) {
		source.sendSuccess(() -> Component.literal(line).withStyle(color), false);
	}
}
