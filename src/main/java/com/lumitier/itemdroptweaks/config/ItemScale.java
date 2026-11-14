package com.lumitier.itemdroptweaks.config;

import net.minecraft.item.Item;

/**
 * Eine Zeile der Liste "Groesse je Item": welches Item, welcher Faktor.
 *
 * WARUM EIN RECORD UND KEIN VERAENDERBARES OBJEKT:
 * YACL haelt zu jeder Option einen schwebenden Wert, der erst beim
 * Speichern wirksam wird, und vergleicht ihn mit dem alten, um "geaendert"
 * anzuzeigen. Das funktioniert nur, wenn der Wert unveraenderlich ist und
 * equals besitzt. Wuerde das Widget stattdessen in einem gemeinsamen Objekt
 * herumschreiben, waere die Aenderung sofort da - und Abbrechen haette
 * keine Wirkung mehr.
 *
 * @param item   das Item, fuer das die Einstellungen gelten
 * @param scale  Faktor zwischen ItemDropTweaks.ITEM_MIN und ITEM_MAX
 * @param glow   Eigenleuchten 0 bis 15
 * @param shadow waechst der Schatten mit der Groesse mit?
 */
public record ItemScale(Item item, float scale, int glow, boolean shadow) {

	/** Dieselbe Zeile mit anderem Item. */
	public ItemScale withItem(Item other) {
		return new ItemScale(other, scale, glow, shadow);
	}

	/** Dieselbe Zeile mit anderer Groesse. */
	public ItemScale withScale(float other) {
		return new ItemScale(item, other, glow, shadow);
	}
}
