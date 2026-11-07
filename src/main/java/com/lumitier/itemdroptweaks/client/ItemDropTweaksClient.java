package com.lumitier.itemdroptweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-Entrypoint.
 *
 * Phase 1: Lade-Nachweis und Config, noch keine Renderer-Hooks.
 */
public class ItemDropTweaksClient implements ClientModInitializer {

	public static final String MOD_ID = "itemdroptweaks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		com.lumitier.itemdroptweaks.ItemDropTweaks.load();
		LOGGER.info("[Item Drop Tweaks] Client-Init ok. (Phase 1: Geruest + Config, noch keine Renderer-Hooks.)");

		// LumiLib ist optional - deshalb erst fragen, dann anfassen.
		// Der Fang um Throwable ist Absicht: LumiLib hat einen eigenen
		// Versionsrhythmus. Aendert sich dort etwas, soll DIESE Mod
		// weiterlaufen und nur nicht im Hub auftauchen.
		if (FabricLoader.getInstance().isModLoaded("lumilib")) {
			try {
				LumiLibCompat.register();
			} catch (Throwable t) {
				LOGGER.warn("[Item Drop Tweaks] LumiLib-Anmeldung fehlgeschlagen.", t);
			}
		}
	}
}
