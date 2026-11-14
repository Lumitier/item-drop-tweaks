package com.lumitier.itemdroptweaks.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Kleines Fenster zum Aussuchen eines Items: Suchfeld oben, darunter ein
 * Raster aller Items.
 *
 * WOZU, es gab doch schon "Item aus der Hand":
 * Die Hand gibt es nur in einer Welt. Wer die Einstellungen im Hauptmenue
 * oeffnet, hatte vorher keine Moeglichkeit, das Item einer Zeile zu
 * aendern. Hier geht es ueberall.
 *
 * WARUM EIN EIGENES FENSTER UND KEIN AUFKLAPPMENUE:
 * YACL bringt zwar eine Item-Auswahl mit, aber als eigenstaendiges
 * Bedienelement fuer eine ganze Zeile. In einer Zeile, die daneben schon
 * Bild, Name und Regler traegt, ist dafuer kein Platz. Ein Fenster davor
 * loest das, ohne YACL nachzubauen.
 *
 * ZUR LISTE:
 * Sie wird einmal beim Oeffnen gebaut, nicht pro Bild. Luft bleibt dabei
 * aussen vor - ein unsichtbares Item als Drop-Groesse einzustellen ergibt
 * keinen Sinn.
 */
public final class ItemPickerScreen extends Screen {

	private static final int CELL = 18;
	private static final int COLUMNS = 13;
	private static final int ROWS = 8;
	private static final int TOP = 56;

	private final Screen parent;
	private final Consumer<Item> onPick;
	/** Items, die eine andere Zeile schon belegt - ausgegraut und gesperrt. */
	private final Predicate<Item> blocked;

	/** Alle Items, einmal gesammelt. */
	private final List<Item> all = new ArrayList<>();
	/** Was gerade zur Suche passt. */
	private final List<Item> shown = new ArrayList<>();

	private TextFieldWidget search;
	private int scroll;

	public ItemPickerScreen(Screen parent, Consumer<Item> onPick, Predicate<Item> blocked) {
		super(Text.translatable("itemdroptweaks.picker.title"));
		this.parent = parent;
		this.onPick = onPick;
		this.blocked = blocked;
	}

	@Override
	protected void init() {
		if (all.isEmpty()) {
			for (Item item : Registries.ITEM) {
				if (item != net.minecraft.item.Items.AIR) {
					all.add(item);
				}
			}
		}

		int gridW = COLUMNS * CELL;
		int left = (this.width - gridW) / 2;

		this.search = new TextFieldWidget(this.textRenderer, left, 30, gridW, 18,
			Text.translatable("itemdroptweaks.picker.search"));
		this.search.setMaxLength(48);
		this.search.setPlaceholder(Text.translatable("itemdroptweaks.picker.search"));
		this.search.setChangedListener(q -> {
			scroll = 0;
			filter(q);
		});
		addDrawableChild(this.search);
		setInitialFocus(this.search);

		filter(this.search.getText());
	}

	/** Trefferliste neu bauen. Sucht in Anzeigename und Kennung. */
	private void filter(String query) {
		shown.clear();
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		for (Item item : all) {
			if (q.isEmpty()
				|| item.getName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| Registries.ITEM.getId(item).toString().contains(q)) {
				shown.add(item);
			}
		}
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int gridW = COLUMNS * CELL;
		int left = (this.width - gridW) / 2;

		context.drawCenteredTextWithShadow(this.textRenderer, this.title,
			this.width / 2, 12, 0xFFFFFFFF);

		Item held = heldItem();

		// Raster
		int first = scroll * COLUMNS;
		for (int i = 0; i < COLUMNS * ROWS; i++) {
			int index = first + i;
			if (index >= shown.size()) {
				break;
			}
			int x = left + (i % COLUMNS) * CELL;
			int y = TOP + (i / COLUMNS) * CELL;

			Item item = shown.get(index);
			boolean used = blocked.test(item);
			boolean hover = !used && mouseX >= x && mouseX < x + CELL
				&& mouseY >= y && mouseY < y + CELL;
			if (hover) {
				context.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
			}
			context.drawItem(item.getDefaultStack(), x + 1, y + 1);
			// Belegtes Item: dunkler Schleier darueber, Klick geht ins Leere.
			if (used) {
				context.fill(x, y, x + CELL, y + CELL, 0xB0101010);
			}
		}

		// Fusszeile: Trefferzahl und Hinweis zum Blaettern.
		String info = shown.size() + " / " + all.size();
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(info),
			this.width / 2, TOP + ROWS * CELL + 6, 0xFFA0A0A0);

		// Quickpick-Feld unter dem Raster, nur in einer Welt.
		if (held != null) {
			int qx = left;
			int qy = TOP + ROWS * CELL + 20;
			boolean usedHand = blocked.test(held);
			boolean hover = !usedHand && mouseX >= qx && mouseX < qx + gridW
				&& mouseY >= qy && mouseY < qy + CELL;
			context.fill(qx, qy, qx + gridW, qy + CELL,
				usedHand ? 0x20FFFFFF : (hover ? 0x60FFFFFF : 0x30FFFFFF));
			context.drawItem(held.getDefaultStack(), qx + 1, qy + 1);
			context.drawText(this.textRenderer,
				Text.translatable("itemdroptweaks.picker.in_hand", held.getName()),
				qx + CELL + 4, qy + (CELL - this.textRenderer.fontHeight) / 2 + 1,
				usedHand ? 0xFF808080 : 0xFFFFFFFF, true);
		}
	}

	// ------------------------------------------------------------------
	// Bedienen
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();

		int gridW = COLUMNS * CELL;
		int left = (this.width - gridW) / 2;

		// Erst die Schnellauswahl, sie liegt ausserhalb des Rasters.
		Item held = heldItem();
		if (held != null) {
			int qy = TOP + ROWS * CELL + 20;
			if (mouseX >= left && mouseX < left + gridW
				&& mouseY >= qy && mouseY < qy + CELL) {
				if (!blocked.test(held)) {
					pick(held);
				}
				return true;
			}
		}

		int first = scroll * COLUMNS;
		for (int i = 0; i < COLUMNS * ROWS; i++) {
			int index = first + i;
			if (index >= shown.size()) {
				break;
			}
			int x = left + (i % COLUMNS) * CELL;
			int y = TOP + (i / COLUMNS) * CELL;
			if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
				Item item = shown.get(index);
				if (!blocked.test(item)) {
					pick(item);
				}
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
		int maxScroll = Math.max(0, rows - ROWS);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical)));
		return true;
	}

	private void pick(Item item) {
		onPick.accept(item);
		close();
	}

	@Override
	public void close() {
		MinecraftClient client = this.client == null ? MinecraftClient.getInstance() : this.client;
		client.setScreen(parent);
	}

	/** Item in der Haupthand, oder null ausserhalb einer Welt. */
	private static Item heldItem() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return null;
		}
		ItemStack held = client.player.getMainHandStack();
		return held.isEmpty() ? null : held.getItem();
	}
}
