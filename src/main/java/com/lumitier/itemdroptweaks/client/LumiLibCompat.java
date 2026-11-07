package com.lumitier.itemdroptweaks.client;

import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import com.lumitier.lumilib.api.SettingsPage;
import com.lumitier.itemdroptweaks.ItemDropTweaks;
import com.lumitier.itemdroptweaks.config.ItemDropTweaksScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.Text;

/**
 * Anmeldung bei LumiLib.
 *
 * WARUM EINE EIGENE KLASSE - das ist der wichtige Teil:
 * LumiLib ist OPTIONAL. Stuende der Verweis darauf im Initialisierer der
 * Mod, wuerde die Java-Laufzeit beim Start versuchen, LumiLibs Klassen
 * aufzuloesen - und ohne LumiLib startet die Mod gar nicht mehr. Liegt
 * der Verweis dagegen in einer eigenen Klasse, wird sie erst geladen,
 * wenn jemand sie anfasst. Vorher wird abgefragt, ob LumiLib da ist.
 *
 * NAME, VERSION UND BESCHREIBUNG KOMMEN AUS DER fabric.mod.json:
 * Sie stehen dort ohnehin, Mod Menu und Modrinth lesen dieselben Werte.
 * Sie hier noch einmal einzutragen hiesse, zwei Stellen gleich halten zu
 * muessen - und nach dem naechsten Versionssprung stuende im Hub die
 * alte Nummer.
 *
 * ZUM ICON: nichts zu tun. LumiLib liest es aus dem Jar dieser Mod.
 */
public final class LumiLibCompat {

	private LumiLibCompat() {}

	public static void register() {
		ModContainer container = FabricLoader.getInstance()
			.getModContainer(ItemDropTweaks.MOD_ID).orElse(null);

		String version = container == null ? ""
			: container.getMetadata().getVersion().getFriendlyString();
		String name = container == null ? "Item Drop Tweaks"
			: container.getMetadata().getName();
		String summary = container == null ? ""
			: container.getMetadata().getDescription();

		LumiLibApi.register(new LumiMod(
			ItemDropTweaks.MOD_ID,
			Text.literal(name),
			version,
			Text.literal(summary),
			// Kein eigenes Sprite - LumiLib nimmt das Icon aus dem Jar.
			null,
			// Der bestehende YACL-Bildschirm. Sobald LumiLib Schieberegler
			// und Farbwaehler kann, wird daraus eine SettingsPage und die
			// Einstellungen stehen direkt im Hub.
			// YACL bleibt als Rueckfallebene - Mod Menus Zahnrad
			// oeffnet weiterhin diesen Bildschirm.
			ItemDropTweaksScreen::create,
			LumiLibCompat::settings));
	}

	/**
	 * Die Einstellungen, wie LumiLibs Hub sie zeichnet.
	 *
	 * DIESELBEN FELDER wie der YACL-Bildschirm, nur anders beschrieben.
	 * Beide Wege schreiben direkt in die statischen Felder von
	 * ItemDropTweaks und rufen dasselbe save().
	 */
	private static SettingsPage settings() {
		return SettingsPage.builder(ItemDropTweaks.MOD_ID)
			.section("general")
				.toggle("mod_enabled",
					() -> ItemDropTweaks.enabled, v -> ItemDropTweaks.enabled = v, true)
				.toggle("render_drops",
					() -> ItemDropTweaks.renderDrops, v -> ItemDropTweaks.renderDrops = v, true)
				.intSlider("glow", 0, 15, 1,
					() -> ItemDropTweaks.glow, v -> ItemDropTweaks.glow = v, 0)
			.section("scale")
				.slider("scale", 0.1F, 3.0F, 0.05F,
					() -> ItemDropTweaks.scale, v -> ItemDropTweaks.scale = v, 1.0F)
				.toggle("scale_shadow",
					() -> ItemDropTweaks.scaleShadow, v -> ItemDropTweaks.scaleShadow = v, true)
			.section("animation")
				.slider("spin_speed", 0.0F, 5.0F, 0.05F,
					() -> ItemDropTweaks.spinSpeed, v -> ItemDropTweaks.spinSpeed = v, 1.0F)
				.slider("bob_speed", 0.0F, 5.0F, 0.05F,
					() -> ItemDropTweaks.bobSpeed, v -> ItemDropTweaks.bobSpeed = v, 1.0F)
				.slider("bob_height", 0.0F, 3.0F, 0.05F,
					() -> ItemDropTweaks.bobHeight, v -> ItemDropTweaks.bobHeight = v, 1.0F)
			.section("pile")
				.slider("spread", 0.0F, 3.0F, 0.05F,
					() -> ItemDropTweaks.spread, v -> ItemDropTweaks.spread = v, 1.0F)
			.section("stack_size")
				.toggle("copies_by_count",
					() -> ItemDropTweaks.copiesByCount, v -> ItemDropTweaks.copiesByCount = v, false)
				.intSlider("max_copies", 1, 16, 1,
					() -> ItemDropTweaks.maxCopies, v -> ItemDropTweaks.maxCopies = v, 8)
				.toggle("scale_by_count",
					() -> ItemDropTweaks.scaleByCount, v -> ItemDropTweaks.scaleByCount = v, false)
				.slider("scale_max", 1.0F, 3.0F, 0.1F,
					() -> ItemDropTweaks.scaleMax, v -> ItemDropTweaks.scaleMax = v, 1.5F)
				.intSlider("count_max", 64, 100000, 64,
					() -> ItemDropTweaks.countMax, v -> ItemDropTweaks.countMax = v, 1024)
			.save(ItemDropTweaks::save)
			.build();
	}
}
