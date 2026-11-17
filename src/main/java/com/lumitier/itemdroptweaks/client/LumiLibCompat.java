package com.lumitier.itemdroptweaks.client;

import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import com.lumitier.lumilib.api.SettingsPage;
import com.lumitier.itemdroptweaks.ItemDropTweaks;
import com.lumitier.itemdroptweaks.config.ItemDropTweaksScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

		// Server-Regeln (LumiLibControlPlugin). Eigener Fang: Mit einer
		// LumiLib aelter als 1.3.0 gibt es die Methode nicht - dann soll die
		// Mod trotzdem im Hub stehen, nur eben ohne Server-Steuerung.
		try {
			LumiLibApi.onServerRulesChanged(() ->
				ItemDropTweaks.serverDisabled = LumiLibApi.isDisabledByServer(ItemDropTweaks.MOD_ID));
		} catch (Throwable t) {
			ItemDropTweaks.LOGGER.warn("[Item Drop Tweaks] LumiLib is too old for server control (needs 1.3.0+).");
		}
	}

	/**
	 * Die Einstellungen, wie LumiLibs Hub sie zeichnet.
	 *
	 * DIESELBEN FELDER wie der YACL-Bildschirm, nur anders beschrieben.
	 * Beide Wege schreiben direkt in die statischen Felder von
	 * ItemDropTweaks und rufen dasselbe save().
	 */
	// ------------------------------------------------------------------
	// Die Liste "Je Item"
	// ------------------------------------------------------------------

	/**
	 * Die Eintraege, wie der Hub sie zeichnet.
	 *
	 * BEI JEDEM BILD NEU GEBAUT, und das ist Absicht: Hinzufuegen und
	 * Loeschen aendert die Liste, waehrend die Seite offen ist. Eine
	 * einmal gebaute Kopie waere ab dem ersten Klick veraltet.
	 *
	 * Die Unterseite entsteht dagegen NICHT bei jedem Bild - sie haengt
	 * als Supplier dran und wird erst gebaut, wenn der Eintrag
	 * aufgeschlagen wird.
	 */
	private static List<SettingsPage.ListRow.Entry> itemEntries() {
		List<SettingsPage.ListRow.Entry> out = new ArrayList<>();
		for (int i = 0; i < ItemDropTweaks.itemRules.size(); i++) {
			ItemDropTweaks.ItemRule r = ItemDropTweaks.itemRules.get(i);
			if (r.settings == null) {
				continue;
			}
			Item item = resolve(r.item);
			ItemStack icon = buildStack(item, r);

			// Durchgestrichen, wenn eine frueher eingetragene Regel
			// dieselbe Bedingung hat. Dann greift diese hier nie, weil
			// die erste passende gewinnt - und das waere sonst nicht zu
			// sehen, man wuerde Werte einstellen, die nichts tun.
			boolean shadowed = false;
			for (int j = 0; j < i; j++) {
				if (ItemDropTweaks.itemRules.get(j).sameAs(r)) {
					shadowed = true;
					break;
				}
			}

			out.add(SettingsPage.ListRow.Entry.of(icon, label(item, r),
				enchantLine(r), () -> rulePage(r), shadowed));
		}
		return out;
	}

	/** Erste Zeile: nur der Name des Items. */
	private static Text label(Item item, ItemDropTweaks.ItemRule r) {
		return Text.literal(item == null ? r.item : item.getName().getString());
	}

	/**
	 * Zweite Zeile: die Verzauberungen, auf die die Regel zielt.
	 *
	 * EIGENE ZEILE, nicht hinter den Namen gehaengt. Hinter dem Namen las
	 * sich "Elytra Unbreaking III" wie ein zusammengehoeriger Gegenstand,
	 * und man sah nicht, wo der Name aufhoert und die Bedingung anfaengt.
	 *
	 * Ohne Verzauberungen steht hier null statt eines leeren Textes: Der
	 * Eintrag ist dann einzeilig und der Name sitzt mittig, statt oben zu
	 * kleben und darunter nichts zu haben.
	 */
	@Nullable
	private static Text enchantLine(ItemDropTweaks.ItemRule r) {
		if (r.enchants.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : r.enchants.entrySet()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(enchantName(e.getKey())).append(' ').append(roman(e.getValue()));
		}
		return Text.literal(sb.toString());
	}

	/**
	 * Das Bild des Eintrags, mit den Verzauberungen darauf.
	 *
	 * Dadurch bekommt es den Schimmer und sieht aus wie das, worauf die
	 * Regel zielt. Der Stapel wird NUR gezeigt, nie gespeichert.
	 *
	 * Ohne geladene Welt lassen sich Verzauberungen nicht aufloesen -
	 * dann bleibt es das blanke Item. Kein Fehler, nur weniger Schimmer.
	 */
	private static ItemStack buildStack(Item item, ItemDropTweaks.ItemRule r) {
		if (item == null) {
			return null;
		}
		ItemStack stack = item.getDefaultStack();
		if (r.enchants.isEmpty()) {
			return stack;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return stack;
		}
		client.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT)
			.ifPresent(reg -> {
				for (Map.Entry<String, Integer> e : r.enchants.entrySet()) {
					Identifier id = Identifier.tryParse(e.getKey());
					if (id == null) {
						continue;
					}
					reg.getEntry(id).ifPresent(entry ->
						stack.addEnchantment(entry, e.getValue()));
				}
			});
		return stack;
	}

	/**
	 * Anzeigename einer Verzauberung.
	 *
	 * Ueber LumiLib, weil der Name ohne geladene Welt aus dem
	 * Uebersetzungsschluessel gebildet werden muss - diese Logik steht
	 * dort einmal und nicht in jeder Mod noch einmal.
	 */
	private static String enchantName(String key) {
		Identifier id = Identifier.tryParse(key);
		return id == null ? key : LumiLibApi.enchantName(id);
	}

	/**
	 * Roemische Zahl - aber nur bis zehn.
	 *
	 * Darueber die arabische Ziffer: Stufen bis 255 sind moeglich, und
	 * "CCXXXVIII" liest niemand. Minecraft selbst macht es genauso.
	 */
	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	/**
	 * Die Unterseite einer Regel.
	 *
	 * EINE ECHTE SettingsPage, ueber denselben Builder wie die Hauptseite
	 * - damit kommen Zuruecksetzen-Knoepfe und Abschnitte von selbst mit.
	 *
	 * Die Verzauberungen stehen als LISTE da, nicht als Regler. Warum der
	 * Wechsel: Ein Regler kann nur bis zur Hoechststufe der Verzauberung,
	 * und Minecraft erlaubt bis 255. Eine Regel soll aber auch auf ein
	 * Schwert mit Schaerfe 100 passen koennen. Ausserdem war das Entfernen
	 * ueber "Regler auf null" nicht zu erraten - jetzt steht ein
	 * Muelleimer daneben, wie bei den Items darueber.
	 */
	private static SettingsPage rulePage(ItemDropTweaks.ItemRule r) {
		ItemDropTweaks.ItemSettings v = r.settings;
		return SettingsPage.builder(ItemDropTweaks.MOD_ID)
			.section("rule")
				.slider("item_scale", ItemDropTweaks.ITEM_MIN, ItemDropTweaks.ITEM_MAX,
					0.05F, () -> v.scale, x -> {
						v.scale = x;
						apply();
					}, ItemDropTweaks.scale)
				.intSlider("item_glow", 0, 15, 1, () -> v.glow, x -> {
					v.glow = x;
					apply();
				}, ItemDropTweaks.glow)
				.toggle("item_shadow", () -> v.shadow, x -> {
					v.shadow = x;
					apply();
				}, ItemDropTweaks.scaleShadow)
			.section("rule_enchants")
				.list("enchant_list", () -> enchantEntries(r),
					() -> openEnchantPicker(r), i -> removeEnchant(r, i), 0)
			.save(ItemDropTweaks::save)
			.build();
	}

	/**
	 * Die Verzauberungen einer Regel als Listeneintraege.
	 *
	 * KEINE UNTERSEITE je Eintrag, sondern onClick: Dahinter steckt genau
	 * ein Wert, die Stufe. Eine eigene Seite dafuer waere ein Klick zu
	 * viel. Der Klick oeffnet stattdessen direkt LumiLibs Stufenabfrage,
	 * mit der bisherigen Stufe schon im Feld.
	 */
	private static List<SettingsPage.ListRow.Entry> enchantEntries(
		ItemDropTweaks.ItemRule r) {

		List<SettingsPage.ListRow.Entry> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : r.enchants.entrySet()) {
			String key = e.getKey();
			int level = e.getValue();
			out.add(SettingsPage.ListRow.Entry.action(
				BOOK,
				Text.literal(enchantName(key) + " " + roman(level)),
				() -> editEnchant(r, key, level)));
		}
		return out;
	}

	/**
	 * Das Bild neben einer Verzauberungszeile.
	 *
	 * EINMAL GEBAUT, nicht je Bild: Die Eintraege entstehen bei jedem
	 * Bild neu, und ein ItemStack je Zeile und Bild waere Muell fuer
	 * nichts - das Buch sieht immer gleich aus.
	 */
	private static final ItemStack BOOK =
		net.minecraft.item.Items.ENCHANTED_BOOK.getDefaultStack();

	/** Stufe einer gesetzten Verzauberung aendern. */
	private static void editEnchant(ItemDropTweaks.ItemRule r, String key, int current) {
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return;
		}
		LumiLibApi.openEnchantLevel(MinecraftClient.getInstance().currentScreen,
			id, current,
			(picked, level) -> {
				r.enchants.put(picked.toString(), level);
				apply();
			});
	}

	/** Entfernt die Verzauberung mit dieser Nummer aus der Regel. */
	private static void removeEnchant(ItemDropTweaks.ItemRule r, int index) {
		List<String> keys = new ArrayList<>(r.enchants.keySet());
		if (index < 0 || index >= keys.size()) {
			return;
		}
		r.enchants.remove(keys.get(index));
		apply();
	}

	/** Item zu einer Kennung, oder null wenn es die Mod nicht gibt. */
	private static Item resolve(String key) {
		Identifier id = Identifier.tryParse(key);
		return id != null && Registries.ITEM.containsId(id)
			? Registries.ITEM.get(id)
			: null;
	}

	/**
	 * Oeffnet LumiLibs Item-Waehler.
	 *
	 * NICHTS IST MEHR GESPERRT: Dasselbe Item darf mehrfach vorkommen,
	 * solange sich die Verzauberungen unterscheiden. Eine zweite blanke
	 * Regel ist zwar wirkungslos, aber sie zu verbieten hiesse, den
	 * ueblichen Weg zu versperren - man traegt das Item ein und haengt
	 * die Verzauberung danach dran. Bis dahin ist der Eintrag
	 * durchgestrichen, das sagt genug.
	 */
	private static void openPicker() {
		LumiLibApi.openItemPicker(MinecraftClient.getInstance().currentScreen,
			item -> {
				ItemDropTweaks.itemRules.add(new ItemDropTweaks.ItemRule(
					Registries.ITEM.getId(item).toString(),
					new ItemDropTweaks.ItemSettings(
						ItemDropTweaks.scale,
						ItemDropTweaks.glow,
						ItemDropTweaks.scaleShadow)));
				apply();
			},
			null);
	}

	/** Oeffnet den Verzauberungswaehler fuer diese Regel. */
	private static void openEnchantPicker(ItemDropTweaks.ItemRule r) {
		LumiLibApi.openEnchantPicker(MinecraftClient.getInstance().currentScreen,
			(id, level) -> {
				r.enchants.put(id.toString(), level);
				apply();
			},
			id -> r.enchants.containsKey(id.toString()));
	}

	/** Entfernt die Regel mit dieser Nummer. */
	private static void removeItem(int index) {
		if (index < 0 || index >= ItemDropTweaks.itemRules.size()) {
			return;
		}
		ItemDropTweaks.itemRules.remove(index);
		apply();
	}

	/**
	 * Aufgeloeste Zuordnung neu bauen und speichern.
	 *
	 * BEIDES ZUSAMMEN, immer: Ohne rebuildItemIndex liest der
	 * Zeichenpfad weiter die alten Werte, ohne save ist die Aenderung
	 * beim naechsten Start wieder weg.
	 */
	private static void apply() {
		ItemDropTweaks.rebuildItemIndex();
		ItemDropTweaks.save();
	}

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
			.section("per_item")
				.list("item_list", LumiLibCompat::itemEntries,
					LumiLibCompat::openPicker, LumiLibCompat::removeItem, 0)
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
