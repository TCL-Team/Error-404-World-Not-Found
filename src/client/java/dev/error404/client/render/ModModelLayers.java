package dev.error404.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

import dev.error404.Error404;

public final class ModModelLayers {
	private ModModelLayers() {
	}

	public static final ModelLayerLocation ERROR_422 =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath(Error404.MOD_ID, "error_422"), "main");

	public static void register() {
		EntityModelLayerRegistry.registerModelLayer(ERROR_422, Error422Model::createBodyLayer);
	}
}
