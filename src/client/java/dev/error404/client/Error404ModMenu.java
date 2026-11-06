package dev.error404.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import dev.error404.client.config.Error404ConfigScreen;

/**
 * Mod Menu entrypoint. Mod Menu is a {@code suggests} dependency, so this class is only ever
 * loaded when Mod Menu is actually installed - the config screen is also reachable without it
 * through {@code /error404 config}.
 */
@Environment(EnvType.CLIENT)
public final class Error404ModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return Error404ConfigScreen::new;
	}
}
