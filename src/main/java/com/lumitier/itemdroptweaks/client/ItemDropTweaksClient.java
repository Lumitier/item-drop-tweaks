package com.lumitier.itemdroptweaks.client;

import net.fabricmc.api.ClientModInitializer;
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
	}
}
