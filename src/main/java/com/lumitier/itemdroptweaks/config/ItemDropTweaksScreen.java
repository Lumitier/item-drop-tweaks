package com.lumitier.itemdroptweaks.config;

import com.lumitier.itemdroptweaks.ItemDropTweaks;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * YACL-Config-Screen, gleicher Aufbau wie bei Experience Orb Tweaks:
 *   Tab "General"    - nur der Hauptschalter
 *   Tab "Item Drops" - alle Einstellungen in aufklappbaren Gruppen
 *
 * Verifiziert gegen das YACL-Jar 3.8.2+1.21.11-fabric (javap):
 *   OptionGroup.createBuilder().name(..).collapsed(boolean).option(..).build()
 *   ColorControllerBuilder.create(Option&lt;java.awt.Color&gt;).allowAlpha(boolean)
 *   IntegerSliderControllerBuilder.create(Option&lt;Integer&gt;)
 *   FloatSliderControllerBuilder.create(Option&lt;Float&gt;)
 *   BooleanControllerBuilder.create(Option&lt;Boolean&gt;).yesNoFormatter().coloured(boolean)
 *
 * Alle vier Helfer stehen schon bereit, damit Phase 2 nur noch Optionen
 * eintragen muss. Der zweite Tab kommt dann dazu - ein Tab ohne Optionen
 * waere jetzt nur eine leere Seite.
 */
public final class ItemDropTweaksScreen {

	private ItemDropTweaksScreen() {}

	private static Text key(String id) {
		return Text.translatable("itemdroptweaks.option." + id);
	}

	private static OptionDescription desc(String id) {
		return OptionDescription.createBuilder()
			.text(Text.translatable("itemdroptweaks.option." + id + ".desc"))
			.build();
	}

	private static Option<Boolean> toggle(String id, boolean def,
	                                      java.util.function.Supplier<Boolean> getter,
	                                      java.util.function.Consumer<Boolean> setter) {
		return Option.<Boolean>createBuilder()
			.name(key(id))
			.description(desc(id))
			.binding(def, getter, setter)
			.controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter().coloured(true))
			.build();
	}

	private static Option<Float> slider(String id,
	                                    float def, float min, float max, float step,
	                                    java.util.function.Supplier<Float> getter,
	                                    java.util.function.Consumer<Float> setter) {
		return Option.<Float>createBuilder()
			.name(key(id))
			.description(desc(id))
			.binding(def, getter, setter)
			.controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step))
			.build();
	}

	private static Option<Integer> intSlider(String id,
	                                         int def, int min, int max, int step,
	                                         java.util.function.Supplier<Integer> getter,
	                                         java.util.function.Consumer<Integer> setter) {
		return Option.<Integer>createBuilder()
			.name(key(id))
			.description(desc(id))
			.binding(def, getter, setter)
			.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
			.build();
	}

	/** Farbwaehler mit Hex-Feld und Alphakanal. */
	@SuppressWarnings("unused")
	private static Option<java.awt.Color> colorPicker(String id, int def,
	                                                  java.util.function.Supplier<Integer> getter,
	                                                  java.util.function.Consumer<Integer> setter) {
		return Option.<java.awt.Color>createBuilder()
			.name(key(id))
			.description(desc(id))
			.binding(
				new java.awt.Color(def, true),
				() -> new java.awt.Color(getter.get(), true),
				v -> setter.accept(v.getRGB()))
			.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
			.build();
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.createBuilder()
			.title(Text.translatable("itemdroptweaks.title"))

			// ================= Tab 1: General =================
			.category(ConfigCategory.createBuilder()
				.name(Text.translatable("itemdroptweaks.category.general"))
				.option(toggle("mod_enabled", true,
					() -> ItemDropTweaks.enabled, v -> ItemDropTweaks.enabled = v))
				.build())

			// ================= Tab 2: Item Drops =================
			.category(ConfigCategory.createBuilder()
				.name(Text.translatable("itemdroptweaks.category.drops"))

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("itemdroptweaks.group.general"))
					.option(toggle("render_drops", true,
						() -> ItemDropTweaks.renderDrops, v -> ItemDropTweaks.renderDrops = v))
					.option(intSlider("glow", 0, 0, 15, 1,
						() -> ItemDropTweaks.glow, v -> ItemDropTweaks.glow = v))
					.build())

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("itemdroptweaks.group.scale"))
					.option(slider("scale", 1.0F, 0.1F, 3.0F, 0.05F,
						() -> ItemDropTweaks.scale, v -> ItemDropTweaks.scale = v))
					.option(toggle("scale_shadow", true,
						() -> ItemDropTweaks.scaleShadow, v -> ItemDropTweaks.scaleShadow = v))
					.build())

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("itemdroptweaks.group.animation"))
					.option(slider("spin_speed", 1.0F, 0.0F, 5.0F, 0.05F,
						() -> ItemDropTweaks.spinSpeed, v -> ItemDropTweaks.spinSpeed = v))
					.option(slider("bob_speed", 1.0F, 0.0F, 5.0F, 0.05F,
						() -> ItemDropTweaks.bobSpeed, v -> ItemDropTweaks.bobSpeed = v))
					.option(slider("bob_height", 1.0F, 0.0F, 3.0F, 0.05F,
						() -> ItemDropTweaks.bobHeight, v -> ItemDropTweaks.bobHeight = v))
					.build())

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("itemdroptweaks.group.pile"))
					.option(slider("spread", 1.0F, 0.0F, 3.0F, 0.05F,
						() -> ItemDropTweaks.spread, v -> ItemDropTweaks.spread = v))
					.build())

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("itemdroptweaks.group.stack_size"))
					.option(toggle("copies_by_count", false,
						() -> ItemDropTweaks.copiesByCount, v -> ItemDropTweaks.copiesByCount = v))
					.option(intSlider("max_copies", 8, 1, 16, 1,
						() -> ItemDropTweaks.maxCopies, v -> ItemDropTweaks.maxCopies = v))
					.option(toggle("scale_by_count", false,
						() -> ItemDropTweaks.scaleByCount, v -> ItemDropTweaks.scaleByCount = v))
					.option(slider("scale_max", 1.5F, 1.0F, 3.0F, 0.1F,
						() -> ItemDropTweaks.scaleMax, v -> ItemDropTweaks.scaleMax = v))
					.option(intSlider("count_max", 1024, 64, 100000, 64,
						() -> ItemDropTweaks.countMax, v -> ItemDropTweaks.countMax = v))
					.build())

				.build())

			.save(ItemDropTweaks::save)
			.build()
			.generateScreen(parent);
	}
}
