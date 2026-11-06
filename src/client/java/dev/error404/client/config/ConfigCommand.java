package dev.error404.client.config;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;

/**
 * {@code /error404config} - opens the settings screen without Mod Menu.
 *
 * <p>The server-side {@code /error404} command is operator-only and lives on the server, so a
 * singleplayer player who is not opped (or a client on a server they do not control) would
 * otherwise have no way to reach the config except by hand-editing a JSON file.</p>
 */
public final class ConfigCommand {
	private ConfigCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root =
					ClientCommandManager.literal("error404config")
							.executes(context -> {
								Minecraft client = Minecraft.getInstance();
								client.execute(() -> client.setScreen(new Error404ConfigScreen(client.screen)));
								return 1;
							});

			dispatcher.register(root);
		});
	}
}
