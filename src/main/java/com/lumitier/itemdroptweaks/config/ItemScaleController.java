package com.lumitier.itemdroptweaks.config;

import com.lumitier.itemdroptweaks.ItemDropTweaks;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.ListOptionEntry;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Eine Listenzeile mit Item-Bild, Name und Regler - alles nebeneinander.
 *
 * WARUM SELBST GEBAUT:
 * YACL gibt einer Listenzeile genau EIN Bedienelement. Entweder die
 * mitgelieferte Item-Auswahl oder einen Regler, nicht beides. Genau das
 * soll die Zeile hier aber koennen, also wird sie von Hand gezeichnet.
 *
 * DER REGLER SIEHT AUS WIE DER VON YACL:
 * Masse und Farben sind aus SliderControllerElement uebernommen - Bahn
 * einen Pixel hoch in Weiss mit dunklem Schatten darunter, Griff vier
 * Pixel breit, Bahnbreite ein Drittel der Zeile. Einziger Unterschied:
 * YACL blendet den Regler erst beim Ueberfahren ein und schiebt dann die
 * Zahl nach links. In einer Liste, in der man Werte vergleicht, ist das
 * laestig - hier steht beides dauerhaft da.
 *
 * WIE EIN ITEM IN DIE ZEILE KOMMT:
 * Beim Anlegen uebernimmt die neue Zeile, was der Spieler gerade haelt.
 * Spaeter aendern geht auf zwei Wegen: Linksklick auf Bild oder Name
 * oeffnet die Auswahl mit Suchfeld, Rechtsklick nimmt sofort das Item aus
 * der Hand. Der erste Weg muss sein, weil es im Hauptmenue keine Hand
 * gibt und die Einstellungen oft genau dort geoeffnet werden.
 *
 * KEIN ITEM ZWEIMAL:
 * Zwei Zeilen fuer dasselbe Item ergaeben zwei Faktoren, von denen nur
 * einer wirken kann. Deshalb sind schon belegte Items in der Auswahl
 * ausgegraut, der Rechtsklick lehnt sie ab, und eine trotzdem doppelte
 * Zeile wird rot beschriftet.
 *
 * ABGELEITET VON AbstractWidget, NICHT VON ControllerWidget:
 * ControllerWidget bringt Name links und Wert rechts schon mit und waere
 * damit im Weg - die Zeile hier teilt sich anders auf. AbstractWidget ist
 * ausserdem die schmalere Flaeche zu YACL: es muessen nur render,
 * setFocused und isFocused kommen, der Rest ist geerbt.
 */
public final class ItemScaleController implements Controller<ItemScale> {

	private final ListOptionEntry<ItemScale> entry;

	public ItemScaleController(ListOptionEntry<ItemScale> entry) {
		this.entry = entry;
	}

	@Override
	public Option<ItemScale> option() {
		return entry;
	}

	@Override
	public Text formatValue() {
		return Text.literal(text(current()));
	}

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dimension) {
		return new Widget(this, dimension);
	}

	/** Schwebender Wert, nie null - YACL fragt ihn auch beim Aufbau schon ab. */
	private ItemScale current() {
		ItemScale v = entry.pendingValue();
		return v == null || v.item() == null ? fallback() : v;
	}

	/**
	 * Wie oft steht dieses Item in der ganzen Liste?
	 *
	 * Gezaehlt statt ueber Objektgleichheit mit der eigenen Zeile
	 * verglichen: YACL reicht an den Erzeuger des Bedienelements nicht
	 * zwingend dasselbe Objekt, das spaeter in der Liste haengt. Ein
	 * Vergleich auf "ist das ich selbst" ginge dann schief und jede Zeile
	 * haette sich selbst als Doppelung gemeldet.
	 *
	 * Geprueft wird gegen die schwebenden Werte, nicht gegen die Config.
	 * Sonst liesse sich im selben Durchgang zweimal dasselbe eintragen,
	 * solange noch nicht gespeichert wurde.
	 */
	private int countOf(Item item) {
		int n = 0;
		for (ListOptionEntry<ItemScale> other : entry.parentGroup().options()) {
			ItemScale v = other.pendingValue();
			if (v != null && v.item() == item) {
				n++;
			}
		}
		return n;
	}

	/** Steht dieses Item schon irgendwo? Fuer die Auswahl und den Rechtsklick. */
	boolean taken(Item item) {
		return countOf(item) > 0;
	}

	/** Steht das Item DIESER Zeile mehrfach da? Dann wird der Name rot. */
	private boolean clashes() {
		return countOf(current().item()) > 1;
	}

	/**
	 * Zahl genau wie YACLs Schieberegler: eine Nachkommastelle, Trenner
	 * nach Sprache, also "1,0" auf Deutsch. Die schmalen Leerzeichen, die
	 * Java bei Tausendern einsetzt, kann Minecrafts Schrift nicht - die
	 * ersetzt YACL durch normale, und hier wird es genauso gemacht.
	 */
	private static String text(ItemScale v) {
		return String.format("%,.1f", v.scale()).replaceAll("[\u00a0\u202F]", " ");
	}

	/**
	 * Vorgabe fuer eine neue Zeile: was in der Hand liegt, sonst Stein.
	 *
	 * Oeffentlich, weil die Liste im Config-Bildschirm dieselbe Vorgabe
	 * braucht, wenn man auf Plus drueckt.
	 */
	public static ItemScale fallback() {
		// Die allgemeinen Werte als Start, damit das Aufnehmen eines Items
		// erst einmal nichts veraendert.
		return new ItemScale(heldItem(),
			ItemDropTweaks.clamp(ItemDropTweaks.scale, ItemDropTweaks.ITEM_MIN, ItemDropTweaks.ITEM_MAX),
			ItemDropTweaks.clampInt(ItemDropTweaks.glow, 0, 15),
			ItemDropTweaks.scaleShadow);
	}

	/** Item in der Haupthand, oder Stein ausserhalb einer Welt. */
	private static Item heldItem() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return Items.STONE;
		}
		ItemStack held = client.player.getMainHandStack();
		return held.isEmpty() ? Items.STONE : held.getItem();
	}

	// ==================================================================
	// Die Zeile selbst
	// ==================================================================

	private static final class Widget extends AbstractWidget {

		/** Kantenlaenge des Item-Bildes - Minecraft zeichnet Items in 16. */
		private static final int ICON = 16;
		/** Wie YACLs getXPadding(). */
		private static final int PAD = 5;
		private static final int GAP = 5;

		/** Wie YACLs getThumbWidth(). */
		private static final int THUMB_W = 4;
		/** Hoehe des Griffs, wie YACLs sliderBounds. */
		private static final int THUMB_H = 10;

		/** Rasterweite des Reglers. 0.05 ergibt 50 Stufen von 0 bis 2,5. */
		private static final float STEP = 0.05F;

		private static final int COL_LIGHT = 0xFFFFFFFF;
		private static final int COL_SHADOW = 0xFF404040;
		/** Rot fuer eine Zeile, deren Item schon weiter oben steht. */
		private static final int COL_CLASH = 0xFFFF5555;

		private final ItemScaleController control;
		private boolean dragging;
		private boolean focused;
		/** Aus dem letzten Zeichnen - der Regler reagiert nur, wenn er da ist. */
		private boolean hovered;

		Widget(ItemScaleController control, Dimension<Integer> dimension) {
			super(dimension);
			this.control = control;
		}

		// --------------------------------------------------------------
		// Zeichnen
		// --------------------------------------------------------------

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			Dimension<Integer> d = getDimension();
			this.hovered = isMouseOver(mouseX, mouseY);
			boolean open = this.hovered || focused;
			drawButtonRect(context, d.x(), d.y(), d.xLimit(), d.yLimit(), open, true);

			ItemScale value = control.current();
			int centreY = d.y() + d.height() / 2;
			int textY = centreY - this.textRenderer.fontHeight / 2;

			// Bild. getDefaultStack baut hier einen Stapel pro Bild - in
			// einem Menue ist das unbedenklich, anders als im Spiel.
			int iconX = d.x() + PAD;
			context.drawItem(value.item().getDefaultStack(), iconX, centreY - ICON / 2);

			// Zahl rechtsbuendig. Liegt die Maus auf der Zeile, rueckt sie
			// genau um die Reglerbreite nach links - wie bei YACL.
			String number = text(value);
			int numberX = d.xLimit() - PAD - this.textRenderer.getWidth(number);
			if (open) {
				numberX -= trackWidth() + 6 + THUMB_W / 2;
			}
			context.drawText(this.textRenderer, number, numberX, textY, COL_LIGHT, true);

			// Name dazwischen, gekuerzt auf den verbleibenden Platz.
			int nameX = iconX + ICON + GAP;
			int room = numberX - GAP - nameX;
			if (room > 0) {
				String name = fit(value.item().getName().getString(), room);
				context.drawText(this.textRenderer, name, nameX, textY,
					control.clashes() ? COL_CLASH : COL_LIGHT, true);
			}

			if (open) {
				drawSlider(context, centreY, value.scale());
			}

			// Mauszeiger: Doppelpfeil ueber der Bahn, weil sich dort nach
			// links und rechts ziehen laesst, Hand ueber Bild und Name, weil
			// das ein Knopf ist.
			//
			// Das muss hier von Hand passieren. Vanilla setzt den Zeiger in
			// ClickableWidget.render, und davon leitet YACLs AbstractWidget
			// nicht ab - YACLs eigene Regler setzen deshalb gar keinen
			// Zeiger. Die Liste drumherum setzt nur ueber ihrem Rollbalken
			// einen, sonst laesst sie ihn stehen, also bleibt dieser hier
			// bestehen.
			if (dragging || (open && overSlider(mouseX, mouseY))) {
				context.setCursor(StandardCursors.RESIZE_EW);
			} else if (this.hovered) {
				context.setCursor(StandardCursors.POINTING_HAND);
			}
		}

		/**
		 * Der Regler, Pixel fuer Pixel wie YACLs eigener.
		 *
		 * Die Zahlen stehen so in YACLs SliderControllerElement: Bahn einen
		 * Pixel hoch ueber der Mitte, Griff vier Pixel breit und zehn hoch,
		 * dazu je ein Pixel Schatten nach rechts unten. Nachgerechnet statt
		 * geschaetzt, damit die Zeile neben einem echten YACL-Regler nicht
		 * auffaellt.
		 */
		private void drawSlider(DrawContext context, int centreY, float value) {
			int x = trackX();
			int w = trackWidth();
			int top = centreY - THUMB_H / 2;
			int bottom = top + THUMB_H;

			// Bahn
			context.fill(x, centreY - 1, x + w, centreY, COL_LIGHT);
			context.fill(x + 1, centreY, x + w + 1, centreY + 1, COL_SHADOW);

			// Griff
			int thumbX = x + (int) (progress(value) * w);
			context.fill(thumbX - THUMB_W / 2 + 1, top + 1,
				thumbX + THUMB_W / 2 + 1, bottom + 1, COL_SHADOW);
			context.fill(thumbX - THUMB_W / 2, top,
				thumbX + THUMB_W / 2, bottom, COL_LIGHT);
		}

		/** Kuerzt auf eine Pixelbreite und haengt Auslassungspunkte an. */
		private String fit(String text, int maxWidth) {
			if (this.textRenderer.getWidth(text) <= maxWidth) {
				return text;
			}
			int room = maxWidth - this.textRenderer.getWidth("...");
			if (room <= 0) {
				return "";
			}
			int end = text.length();
			while (end > 0 && this.textRenderer.getWidth(text.substring(0, end)) > room) {
				end--;
			}
			return text.substring(0, end) + "...";
		}

		// --------------------------------------------------------------
		// Masse - dieselbe Rechnung wie YACLs sliderBounds
		// --------------------------------------------------------------

		private int trackWidth() {
			return getDimension().width() / 3;
		}

		private int trackX() {
			return getDimension().xLimit() - PAD - THUMB_W / 2 - trackWidth();
		}

		/** 0..1, wo der Wert zwischen kleinstem und groesstem Faktor liegt. */
		private static float progress(float value) {
			float span = ItemDropTweaks.ITEM_MAX - ItemDropTweaks.ITEM_MIN;
			if (span <= 0.0F) {
				return 0.0F;
			}
			return clamp((value - ItemDropTweaks.ITEM_MIN) / span);
		}

		private static float clamp(float v) {
			if (Float.isNaN(v)) {
				return 0.0F;
			}
			return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
		}

		/**
		 * Der ganze linke Teil der Zeile, also Bild UND Name.
		 *
		 * Nur das Bild waere ein Ziel von 16 Pixeln in einer 18 Pixel
		 * hohen Zeile - zu klein, um es sicher zu treffen.
		 */
		private boolean overLeft(double mouseX, double mouseY) {
			Dimension<Integer> d = getDimension();
			return mouseX >= d.x() && mouseX < trackX() - 6
				&& mouseY >= d.y() && mouseY < d.yLimit();
		}

		/** Bahn samt der Haelfte des Griffs an beiden Enden. */
		private boolean overSlider(double mouseX, double mouseY) {
			Dimension<Integer> d = getDimension();
			int x = trackX() - THUMB_W / 2;
			return mouseX >= x && mouseX <= x + trackWidth() + THUMB_W
				&& mouseY >= d.y() && mouseY < d.yLimit();
		}

		// --------------------------------------------------------------
		// Bedienen
		// --------------------------------------------------------------

		@Override
		public boolean onMouseClicked(double mouseX, double mouseY, int button) {
			if (!isMouseOver(mouseX, mouseY)) {
				return false;
			}

			// Rechtsklick links: Abkuerzung fuer "nimm, was ich halte".
			if (button == 1 && overLeft(mouseX, mouseY)) {
				return takeHeldItem();
			}
			if (button != 0) {
				return false;
			}
			// Der Regler ist nur sichtbar, solange die Maus auf der Zeile
			// liegt - dann darf er auch nur dann reagieren.
			if ((hovered || focused) && overSlider(mouseX, mouseY)) {
				dragging = true;
				setFromMouse(mouseX);
				playDownSound();
				return true;
			}
			if (overLeft(mouseX, mouseY)) {
				openSettings();
				return true;
			}
			return false;
		}

		/**
		 * Fenster mit allen Einstellungen dieses Items oeffnen.
		 *
		 * Der aktuelle Bildschirm ist der von YACL - er wird gemerkt und
		 * danach wieder gesetzt. Die schwebenden Werte haengen an den
		 * Optionen, nicht am Bildschirm, und ueberleben das.
		 */
		private void openSettings() {
			MinecraftClient client = MinecraftClient.getInstance();
			Screen back = client.currentScreen;
			playDownSound();
			client.setScreen(ItemSettingsScreen.create(back, control.current(),
				// Beim Speichern: das Item kommt aus der Zeile, nicht aus dem
				// Unterbildschirm - dort kann es zwischenzeitlich gewechselt
				// worden sein.
				(scale, glow, shadow) -> control.option().requestSet(
					new ItemScale(control.current().item(), scale, glow, shadow)),
				// Beim Wechseln: sofort uebernehmen, die Werte bleiben stehen.
				item -> control.option().requestSet(control.current().withItem(item)),
				control::taken));
		}

		@Override
		public boolean onMouseDragged(double mouseX, double mouseY, int button,
		                              double deltaX, double deltaY) {
			if (!dragging || button != 0) {
				return false;
			}
			setFromMouse(mouseX);
			return true;
		}

		@Override
		public boolean onMouseReleased(double mouseX, double mouseY, int button) {
			if (!dragging) {
				return false;
			}
			dragging = false;
			return true;
		}

		/** Pfeiltasten verschieben um eine Rasterweite. */
		@Override
		public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
			if (!focused) {
				return false;
			}
			if (keyCode == 263) {        // links
				nudge(-STEP);
				return true;
			}
			if (keyCode == 262) {        // rechts
				nudge(STEP);
				return true;
			}
			return false;
		}

		private void nudge(float by) {
			set(control.current().scale() + by);
		}

		/**
		 * Mausposition auf einen gerasterten Faktor umrechnen.
		 *
		 * Dieselbe Rechnung wie YACLs setValueFromMouse: Abstand vom
		 * linken Bahnende, geteilt durch die Bahnbreite, mal Spanne.
		 */
		private void setFromMouse(double mouseX) {
			int width = trackWidth();
			float span = ItemDropTweaks.ITEM_MAX - ItemDropTweaks.ITEM_MIN;
			double raw = width <= 0 ? 0.0 : (mouseX - trackX()) / (double) width * span;
			set(ItemDropTweaks.ITEM_MIN + (float) raw);
		}

		/** Rastern, begrenzen und als neuen schwebenden Wert melden. */
		private void set(float raw) {
			float stepped = Math.round(raw / STEP) * STEP;
			float value = ItemDropTweaks.clamp(stepped,
				ItemDropTweaks.ITEM_MIN, ItemDropTweaks.ITEM_MAX);
			control.option().requestSet(control.current().withScale(value));
		}

		/** Rechtsklick: Item aus der Hand uebernehmen, wenn noch frei. */
		private boolean takeHeldItem() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return false;
			}
			ItemStack held = client.player.getMainHandStack();
			if (held.isEmpty() || control.taken(held.getItem())) {
				return false;
			}
			control.option().requestSet(control.current().withItem(held.getItem()));
			playDownSound();
			return true;
		}

		// --------------------------------------------------------------
		// Fokus - von Element gefordert, AbstractWidget laesst es offen
		// --------------------------------------------------------------

		@Override
		public boolean isFocused() {
			return focused;
		}

		@Override
		public void setFocused(boolean focused) {
			this.focused = focused;
		}

		@Override
		public void unfocus() {
			this.focused = false;
		}
	}
}
