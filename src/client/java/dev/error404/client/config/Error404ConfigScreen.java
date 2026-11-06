package dev.error404.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.error404.Error404Config;

/**
 * In-game config, opened from Mod Menu (or {@code /error404 config} where a client screen is
 * available). Writes straight to {@code config/error404.json} on every change, so there is no
 * "apply" button to forget.
 *
 * <p>Deliberately not a generic JSON editor: a horror mod's settings are a safety surface.
 * Every destructive switch is a checkbox with a tooltip that says exactly what it destroys,
 * and the two switches a player will reach for first - real block deletion and chaos - are at
 * the top.</p>
 */
public final class Error404ConfigScreen extends Screen {
	private static final Component TITLE = Component.translatable("config.error404.title");

	private final Screen parent;
	private final Error404Config config = Error404Config.get();

	private Button difficultyButton;

	public Error404ConfigScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int y = 40;
		int row = 24;

		// --- the two switches that matter most -------------------------------------
		this.addRenderableWidget(checkbox(left, y, "config.error404.realBlockDeletion",
				config.realBlockDeletion, (box, value) -> {
					config.realBlockDeletion = value;
					Error404Config.save();
				},
				"config.error404.realBlockDeletion.tooltip"));

		this.addRenderableWidget(checkbox(right, y, "config.error404.chaos",
				config.chaos, (box, value) -> {
					config.chaos = value;
					Error404Config.save();
				},
				"config.error404.chaos.tooltip"));

		// --- safety ---------------------------------------------------------------
		y += row;
		this.addRenderableWidget(checkbox(left, y, "config.error404.photosensitivity",
				config.photosensitivity, (box, value) -> {
					config.photosensitivity = value;
					Error404Config.save();
				},
				"config.error404.photosensitivity.tooltip"));

		this.addRenderableWidget(checkbox(right, y, "config.error404.backupOnStart",
				config.backupOnStart, (box, value) -> {
					config.backupOnStart = value;
					Error404Config.save();
				},
				"config.error404.backupOnStart.tooltip"));

		// --- the hostile layer -----------------------------------------------------
		y += row;
		this.addRenderableWidget(checkbox(left, y, "config.error404.banLayer",
				config.banLayer, (box, value) -> {
					config.banLayer = value;
					Error404Config.save();
				},
				"config.error404.banLayer.tooltip"));

		// --- the world being deleted ---------------------------------------------
		y += row;
		this.addRenderableWidget(checkbox(left, y, "config.error404.destructiveEnding",
				config.destructiveEnding, (box, value) -> {
					config.destructiveEnding = value;
					Error404Config.save();
				},
				"config.error404.destructiveEnding.tooltip"));

		this.addRenderableWidget(checkbox(right, y, "config.error404.protectBlockEntities",
				config.protectBlockEntities, (box, value) -> {
					config.protectBlockEntities = value;
					Error404Config.save();
				},
				"config.error404.protectBlockEntities.tooltip"));

		// --- feedback -------------------------------------------------------------
		y += row;
		this.addRenderableWidget(checkbox(left, y, "config.error404.hud",
				config.hud, (box, value) -> {
					config.hud = value;
					Error404Config.save();
				},
				"config.error404.hud.tooltip"));

		this.addRenderableWidget(checkbox(right, y, "config.error404.announceOnJoin",
				config.announceOnJoin, (box, value) -> {
					config.announceOnJoin = value;
					Error404Config.save();
				},
				"config.error404.announceOnJoin.tooltip"));

		// --- pacing sliders -------------------------------------------------------
		y += row;
		this.addRenderableWidget(new IntSlider(left, y, 150,
				"config.error404.secondsPerIntegrityPoint", 5, 300,
				config.secondsPerIntegrityPoint, value -> config.secondsPerIntegrityPoint = value));

		this.addRenderableWidget(new IntSlider(right, y, 150,
				"config.error404.playerSafeRadius", 0, 32,
				config.playerSafeRadius, value -> config.playerSafeRadius = value));

		// --- difficulty -----------------------------------------------------------
		y += row;
		this.difficultyButton = Button.builder(difficultyLabel(), button -> {
			config.difficulty = nextDifficulty(config.difficulty);
			Error404Config.save();
			button.setMessage(difficultyLabel());
		}).bounds(left, y, 150, 20).build();

		this.addRenderableWidget(this.difficultyButton);

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.bounds(right, y, 150, 20).build());
	}

	private static String nextDifficulty(String current) {
		return switch (current) {
			case "chill" -> "standard";
			case "standard" -> "nightmare";
			default -> "chill";
		};
	}

	private Component difficultyLabel() {
		return Component.translatable("config.error404.difficulty", config.difficulty);
	}

	private Checkbox checkbox(int x, int y, String key, boolean value,
			Checkbox.OnValueChange onChange, String tooltipKey) {
		return Checkbox.builder(Component.translatable(key), this.font)
				.pos(x, y)
				.maxWidth(150)
				.selected(value)
				.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
				.onValueChange(onChange)
				.build();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		// the mod's own menu backdrop: the title panorama without any of the title text
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",
						"textures/gui/title/background/panorama_0.png"),
				0, 0, 0.0F, 0.0F, this.width, this.height, 1024, 512, 1024, 512);
		graphics.fill(0, 0, this.width, this.height, 0xB0000000);
		super.render(graphics, mouseX, mouseY, a);

		graphics.drawCenteredString(this.font, TITLE, this.width / 2, 15, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font,
				Component.translatable("config.error404.hint").withStyle(ChatFormatting.DARK_GRAY),
				this.width / 2, this.height - 26, 0xFF8A8A8A);
	}

	@Override
	public void onClose() {
		Error404Config.save();
		this.minecraft.setScreen(this.parent);
	}

	/** Integer slider: no fractions to explain, and the label always shows the live value. */
	private final class IntSlider extends AbstractSliderButton {
		private final String key;
		private final int min;
		private final int max;
		private final java.util.function.IntConsumer setter;

		private IntSlider(int x, int y, int width, String key, int min, int max, int current,
				java.util.function.IntConsumer setter) {
			super(x, y, width, 20, Component.empty(), (current - min) / (double) (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.updateMessage();
		}

		private int currentValue() {
			return (int) Math.round(Mth.lerp(this.value, this.min, this.max));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable(this.key, this.currentValue()));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(this.currentValue());
			Error404Config.save();
		}
	}
}
