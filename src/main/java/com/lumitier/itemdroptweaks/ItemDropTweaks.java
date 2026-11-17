package com.lumitier.itemdroptweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod-weiter Zustand + Config.
 *
 * Statische Felder, damit die Renderer-Mixins im Hot-Path ohne Lookup und ohne
 * Allokation darauf zugreifen koennen. Die YACL-Oberflaeche schreibt direkt
 * hinein (Live-Wirkung), save()/load() persistiert als JSON.
 */
public final class ItemDropTweaks {

	public static final String MOD_ID = "itemdroptweaks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ItemDropTweaks() {}

	// ------------------------------------------------------------------
	// Config-Felder (Phase 1: nur der globale Schalter)
	// ------------------------------------------------------------------

	/** Mod global an/aus. */
	public static volatile boolean enabled = true;

	/**
	 * Vom Server abgeschaltet (LumiLibControlPlugin ueber LumiLib).
	 *
	 * Bewusst ein EIGENES Feld und nicht "enabled = false": Das hier gilt
	 * nur fuer die aktuelle Verbindung und wird nie gespeichert. Sonst
	 * waere die Mod nach dem Besuch eines einzigen Servers dauerhaft aus.
	 */
	public static volatile boolean serverDisabled = false;

	/** Wirkt die Mod gerade? Eigener Schalter UND der Server muessen es erlauben. */
	public static boolean active() {
		return enabled && !serverDisabled;
	}

	// ------------------------------------------------------------------
	// Darstellung. Alle Defaults entsprechen exakt Vanilla - frisch
	// installiert aendert sich nichts.
	// ------------------------------------------------------------------

	/** Fallengelassene Items ueberhaupt zeichnen. */
	public static volatile boolean renderDrops = true;

	/** Eigenleuchten, 0..15. 0 = Vanilla (kein Zuschlag). */
	public static volatile int glow = 0;

	/** Groessenfaktor. 1.0 = Vanilla. */
	public static volatile float scale = 1.0F;

	/** Schatten mit der Groesse mitskalieren. */
	public static volatile boolean scaleShadow = true;

	/** Drehgeschwindigkeit. 1.0 = Vanilla, 0 = steht still. */
	public static volatile float spinSpeed = 1.0F;

	/** Schwebegeschwindigkeit. 1.0 = Vanilla, 0 = eingefroren. */
	public static volatile float bobSpeed = 1.0F;

	/** Schwebehoehe. 1.0 = Vanilla, 0 = kein Auf und Ab. */
	public static volatile float bobHeight = 1.0F;

	/** Streuung der Kopien im Haufen. 1.0 = Vanilla (+-0.15). */
	public static volatile float spread = 1.0F;

	// ------------------------------------------------------------------
	// Stapelgroessen-Darstellung
	//
	// Vanilla: getRenderedAmount() liefert 1/2/3/4/5 bei 1/16/32/48/darueber.
	// Ohne Merge-Mod traegt ein ItemEntity hoechstens einen vollen Stapel, der
	// Deckel bei 48 faellt also kaum auf. Mit Item Clumps o.ae. liegen schnell
	// vierstellige Mengen in einer Entity - und sehen alle gleich aus.
	// ------------------------------------------------------------------

	/** Anzahl der gezeichneten Kopien aus der echten Stapelgroesse ableiten. */
	public static volatile boolean copiesByCount = false;

	/** Obergrenze der Kopien. Vanilla deckelt bei 5. */
	public static volatile int maxCopies = 8;

	/** Grosse Stapel zusaetzlich vergroessern. */
	public static volatile boolean scaleByCount = false;

	/** Maximaler Groessenfaktor am oberen Ende des Bereichs. */
	public static volatile float scaleMax = 1.5F;

	/** Stapelgroesse, die als oberes Ende der Skala gilt. */
	public static volatile int countMax = 1024;

	// ------------------------------------------------------------------
	// Eigene Einstellungen je Item
	// ------------------------------------------------------------------

	/** Kleinster und groesster Faktor, den eine Zeile annehmen darf. */
	public static final float ITEM_MIN = 0.0F;
	public static final float ITEM_MAX = 2.5F;

	/**
	 * Was fuer ein einzelnes Item gilt.
	 *
	 * Diese Werte ERSETZEN die allgemeinen Einstellungen darueber, sie
	 * verrechnen sich nicht mit ihnen. Wer ein Item in die Liste
	 * aufnimmt, will es offensichtlich eigens einstellen - dann soll ein
	 * Schieben am allgemeinen Regler es nicht mehr mitziehen.
	 *
	 * Die Stapelgroessen-Einstellungen bleiben davon unberuehrt, die
	 * stehen in der Oberflaeche unterhalb der Liste.
	 *
	 * Schlichte Klasse mit offenen Feldern, weil GSON sie direkt liest
	 * und schreibt.
	 */
	public static final class ItemSettings {
		public float scale = 1.0F;
		public int glow = 0;
		public boolean shadow = true;

		public ItemSettings() {}

		public ItemSettings(float scale, int glow, boolean shadow) {
			this.scale = scale;
			this.glow = glow;
			this.shadow = shadow;
		}

		public ItemSettings copy() {
			return new ItemSettings(scale, glow, shadow);
		}

		void sanitize() {
			scale = clamp(scale, ITEM_MIN, ITEM_MAX);
			glow = clampInt(glow, 0, 15);
		}
	}

	/**
	 * Was in der Config steht: Item-Kennung auf Einstellungen.
	 *
	 * WARUM ALS TEXT UND NICHT ALS ITEM:
	 * Die Datei wird gelesen, bevor feststeht, welche Mods mitlaufen. Eine
	 * Zeile fuer ein Item aus einer Mod, die gerade fehlt, soll erhalten
	 * bleiben und nicht beim naechsten Speichern verschwinden.
	 *
	 * Reihenfolge bleibt wie eingetragen - deshalb LinkedHashMap.
	 */
	/**
	 * Eine Regel: fuer WELCHES Item, mit WELCHEN Verzauberungen, und was
	 * dann gilt.
	 *
	 * WARUM EINE LISTE UND KEINE TABELLE MEHR:
	 * Frueher stand hinter jeder Item-Kennung genau ein Eintrag. Dasselbe
	 * Item soll jetzt aber mehrfach vorkommen duerfen, solange sich die
	 * Verzauberungen unterscheiden - ein Schwert mit Schaerfe V darf
	 * anders aussehen als ein blankes. Mit einer Tabelle ginge das nicht,
	 * der Schluessel waere doppelt.
	 *
	 * ZUM VERGLEICH DER VERZAUBERUNGEN: Er ist EXAKT. Eine Regel fuer
	 * "Schaerfe V" greift NICHT bei einem Schwert mit Schaerfe V und
	 * zusaetzlich Haltbarkeit III. Das ist so gewollt: Sonst waere nicht
	 * eindeutig, welche von zwei passenden Regeln gilt, und die Antwort
	 * haenge an der Reihenfolge in der Datei.
	 *
	 * Schlichte Klasse mit offenen Feldern, weil GSON sie direkt liest.
	 */
	public static final class ItemRule {
		public String item = "";
		/** Kennung der Verzauberung auf ihre Stufe. Leer heisst: keine. */
		public Map<String, Integer> enchants = new LinkedHashMap<>();
		public ItemSettings settings = new ItemSettings();

		public ItemRule() {}

		public ItemRule(String item, ItemSettings settings) {
			this.item = item;
			this.settings = settings;
		}

		/** Hat diese Regel dieselbe Bedingung wie jene? */
		public boolean sameAs(ItemRule other) {
			return other != null
				&& java.util.Objects.equals(item, other.item)
				&& enchants.equals(other.enchants);
		}
	}

	/**
	 * Die Regeln, in der Reihenfolge, in der sie eingetragen wurden.
	 *
	 * Die Kennung bleibt Text und wird nicht zu einem Item aufgeloest:
	 * Die Datei wird gelesen, bevor feststeht, welche Mods mitlaufen. Eine
	 * Regel fuer ein Item aus einer fehlenden Mod soll erhalten bleiben
	 * und nicht beim naechsten Speichern verschwinden.
	 */
	public static final List<ItemRule> itemRules = new ArrayList<>();

	/**
	 * Dieselben Werte, aber auf echte Items aufgeloest.
	 *
	 * WOZU ZWEIMAL:
	 * Der Zeichenpfad fragt einmal pro Item und Bild nach. Ueber die
	 * Kennung zu gehen hiesse, pro Abfrage einen String zu bauen - Muell
	 * im Hot-Path. Hier liegt stattdessen eine fertige Zuordnung, die nur
	 * beim Laden und beim Speichern neu gebaut wird.
	 *
	 * volatile, weil der Config-Bildschirm sie ersetzt, waehrend der
	 * Renderthread liest. Ersetzt wird immer die ganze Map, nie ein
	 * Eintrag darin - so sieht der Leser entweder die alte oder die neue,
	 * nie eine halb umgebaute.
	 */
	private static volatile Map<Item, List<ItemRule>> itemIndex = Map.of();

	/**
	 * Regeln fuer diesen Stapel, oder null.
	 *
	 * LAEUFT EINMAL JE ITEM UND BILD, also sparsam gebaut:
	 *
	 * Zuerst eine einzige Abfrage in der Tabelle. Fuer das allermeiste,
	 * was auf dem Boden liegt, gibt es gar keine Regel - dann ist hier
	 * Schluss, ohne die Verzauberungen auch nur anzusehen.
	 *
	 * Die Verzauberungen werden erst geholt, wenn es ueberhaupt
	 * Kandidaten gibt, und dann nur einmal fuer alle.
	 */
	public static ItemSettings settingsFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		List<ItemRule> rules = itemIndex.get(stack.getItem());
		if (rules == null) {
			return null;
		}
		ItemEnchantmentsComponent have = EnchantmentHelper.getEnchantments(stack);
		for (ItemRule r : rules) {
			if (matches(r, have)) {
				return r.settings;
			}
		}
		return null;
	}

	/** Exakter Vergleich: gleiche Verzauberungen, gleiche Stufen, nichts sonst. */
	private static boolean matches(ItemRule rule, ItemEnchantmentsComponent have) {
		if (rule.enchants.size() != have.getSize()) {
			return false;
		}
		if (rule.enchants.isEmpty()) {
			return true;
		}
		for (RegistryEntry<Enchantment> e : have.getEnchantments()) {
			// getIdAsString ist eine Standardmethode ohne Optional - im
			// Zeichenpfad zaehlt, dass hier nichts zusaetzlich entsteht.
			Integer want = rule.enchants.get(e.getIdAsString());
			if (want == null || want != have.getLevel(e)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Aufgeloeste Zuordnung neu bauen. Nach dem Laden und nach jeder
	 * Aenderung im Config-Bildschirm aufrufen.
	 *
	 * Unbekannte Kennungen werden uebersprungen, bleiben aber in
	 * itemRules stehen - siehe oben.
	 */
	public static void rebuildItemIndex() {
		Map<Item, List<ItemRule>> next = new LinkedHashMap<>();
		for (ItemRule r : itemRules) {
			Identifier id = Identifier.tryParse(r.item);
			if (id == null || !Registries.ITEM.containsId(id) || r.settings == null) {
				continue;
			}
			r.settings.sanitize();
			next.computeIfAbsent(Registries.ITEM.get(id), k -> new ArrayList<>()).add(r);
		}
		// Regeln MIT Verzauberungen zuerst pruefen. Sonst faenge eine
		// blanke Regel fuer dasselbe Item nie etwas - sie wuerde zwar
		// wegen des exakten Vergleichs nicht faelschlich greifen, aber
		// die Reihenfolge so zu legen spart im Normalfall Vergleiche.
		for (List<ItemRule> list : next.values()) {
			list.sort((a, b) -> Integer.compare(b.enchants.size(), a.enchants.size()));
		}
		itemIndex = next;
	}

	/** 0..1, wie weit ein Stapel im konfigurierten Bereich liegt. */
	public static float fraction(int count) {
		int max = clampInt(countMax, 64, 100000);
		if (count <= 1) {
			return 0.0F;
		}
		// Logarithmisch: Stapelgroessen wachsen exponentiell, linear waere die
		// Skala schon bei einem mittelgrossen Haufen am Anschlag.
		float f = (float) (Math.log(count) / Math.log(max));
		return clamp(f, 0.0F, 1.0F);
	}

	/** Anzahl zu zeichnender Kopien, 1 .. maxCopies. */
	public static int copiesFor(int count) {
		int max = clampInt(maxCopies, 1, 16);
		return clampInt(1 + Math.round((max - 1) * fraction(count)), 1, max);
	}

	/** Allgemeine Groesse mal Stapelgroessen-Faktor, ohne Eintrag in der Liste. */
	public static float totalScale(int count) {
		return totalScale(count, null);
	}

	/**
	 * Was der Renderer wirklich nimmt.
	 *
	 * Steht das Item in der Liste, gilt dessen Groesse ALLEIN - die
	 * allgemeine Groesse darueber wird uebergangen. Die
	 * Stapelgroessen-Einstellung wirkt weiter, die gehoert nicht zu den
	 * Einstellungen, die ein Listeneintrag ersetzt.
	 */
	public static float totalScale(int count, ItemSettings own) {
		float f = own != null
			? clamp(own.scale, ITEM_MIN, ITEM_MAX)
			: clamp(scale, 0.1F, 3.0F);
		if (scaleByCount) {
			f *= scaleFor(count);
		}
		// 0 ist erlaubt und heisst unsichtbar - das ist eine gueltige
		// Einstellung, kein Fehler.
		return clamp(f, 0.0F, 3.0F);
	}

	/** Groessenfaktor nach Stapelgroesse, 1.0 .. scaleMax. */
	public static float scaleFor(int count) {
		float max = clamp(scaleMax, 1.0F, 3.0F);
		return 1.0F + (max - 1.0F) * fraction(count);
	}

	// ------------------------------------------------------------------
	// Clamp-Helfer
	// ------------------------------------------------------------------

	public static float clamp(float v, float min, float max) {
		if (Float.isNaN(v)) {
			return min;
		}
		return v < min ? min : (v > max ? max : v);
	}

	public static int clampInt(int v, int min, int max) {
		return v < min ? min : (v > max ? max : v);
	}

	/** Alle Werte in gueltige Bereiche zwingen. */
	public static void sanitize() {
		glow      = clampInt(glow, 0, 15);
		scale     = clamp(scale,     0.1F, 3.0F);
		spinSpeed = clamp(spinSpeed, 0.0F, 5.0F);
		bobSpeed  = clamp(bobSpeed,  0.0F, 5.0F);
		bobHeight = clamp(bobHeight, 0.0F, 3.0F);
		spread    = clamp(spread,    0.0F, 3.0F);
		maxCopies = clampInt(maxCopies, 1, 16);
		scaleMax  = clamp(scaleMax, 1.0F, 3.0F);
		countMax  = clampInt(countMax, 64, 100000);
		for (ItemRule r : itemRules) {
			ItemSettings v = r.settings;
			if (v == null) {
				continue;
			}
			if (v != null) {
				v.sanitize();
			}
		}
	}

	// ------------------------------------------------------------------
	// Persistenz (GSON kommt mit Minecraft)
	// ------------------------------------------------------------------

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	/** Reines Daten-Transferobjekt fuer JSON. */
	private static final class Data {
		boolean enabled = true;
		boolean renderDrops = true;
		int glow = 0;
		float scale = 1.0F;
		boolean scaleShadow = true;
		float spinSpeed = 1.0F;
		float bobSpeed = 1.0F;
		float bobHeight = 1.0F;
		float spread = 1.0F;
		boolean copiesByCount = false;
		int maxCopies = 8;
		boolean scaleByCount = false;
		float scaleMax = 1.5F;
		int countMax = 1024;
		/**
		 * Rohform, damit auch die alte Fassung noch gelesen wird: dort
		 * stand hinter der Kennung nur eine Zahl, heute ein Block mit
		 * Groesse, Leuchten und Schatten.
		 */
		Map<String, JsonElement> itemScales = new LinkedHashMap<>();

		/**
		 * Die heutige Form. Fehlt sie, wird aus itemScales uebernommen -
		 * siehe load(). Beides gleichzeitig zu schreiben waere eine
		 * zweite Wahrheit, deshalb steht in neuen Dateien nur noch das
		 * hier.
		 */
		List<ItemRule> itemRules = new ArrayList<>();
	}

	/**
	 * Einen Eintrag aus der Datei lesen.
	 *
	 * Alte Fassung: nur eine Zahl, die Groesse. Leuchten und Schatten
	 * bekommen dann die Vorgaben, damit sich an einem bestehenden Pack
	 * nichts aendert.
	 */
	private static ItemSettings readSettings(JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return null;
		}
		try {
			if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
				return new ItemSettings(raw.getAsFloat(), 0, true);
			}
			return GSON.fromJson(raw, ItemSettings.class);
		} catch (Exception e) {
			return null;
		}
	}

	public static void load() {
		Path p = configPath();
		try {
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) {
					Data d = GSON.fromJson(r, Data.class);
					if (d != null) {
						enabled       = d.enabled;
						renderDrops   = d.renderDrops;
						glow          = d.glow;
						scale         = d.scale;
						scaleShadow   = d.scaleShadow;
						spinSpeed     = d.spinSpeed;
						bobSpeed      = d.bobSpeed;
						bobHeight     = d.bobHeight;
						spread        = d.spread;
						copiesByCount = d.copiesByCount;
						maxCopies     = d.maxCopies;
						scaleByCount  = d.scaleByCount;
						scaleMax      = d.scaleMax;
						countMax      = d.countMax;
						itemRules.clear();
						if (d.itemRules != null && !d.itemRules.isEmpty()) {
							for (ItemRule rule : d.itemRules) {
								if (rule == null || rule.item == null || rule.item.isEmpty()) {
									continue;
								}
								if (rule.enchants == null) {
									rule.enchants = new LinkedHashMap<>();
								}
								if (rule.settings == null) {
									rule.settings = new ItemSettings();
								}
								itemRules.add(rule);
							}
						} else if (d.itemScales != null) {
							// UEBERNAHME DER ALTEN FORM: Dort stand hinter
							// jeder Kennung genau ein Eintrag, ohne
							// Verzauberungen. Daraus wird je eine Regel ohne
							// Bedingung - die wirkt genau wie vorher.
							for (Map.Entry<String, JsonElement> e : d.itemScales.entrySet()) {
								ItemSettings v = readSettings(e.getValue());
								if (e.getKey() != null && v != null) {
									itemRules.add(new ItemRule(e.getKey(), v));
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("[Item Drop Tweaks] Config konnte nicht geladen werden, nutze Defaults.", e);
		}
		sanitize();
		rebuildItemIndex();
		save();
	}

	public static void save() {
		sanitize();
		rebuildItemIndex();
		Path p = configPath();
		try {
			Files.createDirectories(p.getParent());
			Data d = new Data();
			d.enabled       = enabled;
			d.renderDrops   = renderDrops;
			d.glow          = glow;
			d.scale         = scale;
			d.scaleShadow   = scaleShadow;
			d.spinSpeed     = spinSpeed;
			d.bobSpeed      = bobSpeed;
			d.bobHeight     = bobHeight;
			d.spread        = spread;
			d.copiesByCount = copiesByCount;
			d.maxCopies     = maxCopies;
			d.scaleByCount  = scaleByCount;
			d.scaleMax      = scaleMax;
			d.countMax      = countMax;
			// itemScales bleibt leer: Eine neu geschriebene Datei soll nur
			// noch die heutige Form enthalten. Gelesen wird die alte
			// weiterhin, siehe load().
			d.itemScales = new LinkedHashMap<>();
			d.itemRules  = new ArrayList<>(itemRules);
			try (Writer w = Files.newBufferedWriter(p)) {
				GSON.toJson(d, w);
			}
		} catch (Exception e) {
			LOGGER.warn("[Item Drop Tweaks] Config konnte nicht gespeichert werden.", e);
		}
	}
}
