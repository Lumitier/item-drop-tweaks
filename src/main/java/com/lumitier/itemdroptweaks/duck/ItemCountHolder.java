package com.lumitier.itemdroptweaks.duck;

/**
 * Wird per Mixin auf ItemStackEntityRenderState aufgesetzt.
 *
 * Vanilla legt im RenderState nur renderedAmount ab (1..5), nicht die echte
 * Stapelgroesse. getRenderedAmount() deckelt bei 48 - ein Stapel mit 2304
 * Items ist von einem mit 49 nicht zu unterscheiden. Hier wird der Rohwert
 * zusaetzlich mitgefuehrt.
 */
public interface ItemCountHolder {

	int itemdroptweaks$getCount();

	void itemdroptweaks$setCount(int count);

	/**
	 * Fertige Groesse dieses Drops, inklusive Listeneintrag und
	 * Stapelgroesse.
	 *
	 * Wird einmal je Item und Bild in updateRenderState ausgerechnet und
	 * hier abgelegt. Der Zeichenpfad liest nur noch diesen Float und
	 * rechnet nichts mehr - kein Muell pro Bild.
	 */
	float itemdroptweaks$getItemScale();

	void itemdroptweaks$setItemScale(float factor);
}
