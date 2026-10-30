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
}
