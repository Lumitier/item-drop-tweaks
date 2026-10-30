package com.lumitier.itemdroptweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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

	/** Basisgroesse mal Stapelgroessen-Faktor - was der Renderer wirklich nimmt. */
	public static float totalScale(int count) {
		float f = clamp(scale, 0.1F, 3.0F);
		if (scaleByCount) {
			f *= scaleFor(count);
		}
		return f;
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
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("[Item Drop Tweaks] Config konnte nicht geladen werden, nutze Defaults.", e);
		}
		sanitize();
		save();
	}

	public static void save() {
		sanitize();
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
			try (Writer w = Files.newBufferedWriter(p)) {
				GSON.toJson(d, w);
			}
		} catch (Exception e) {
			LOGGER.warn("[Item Drop Tweaks] Config konnte nicht gespeichert werden.", e);
		}
	}
}
