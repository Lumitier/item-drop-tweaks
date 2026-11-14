package com.lumitier.itemdroptweaks.config;

import com.lumitier.itemdroptweaks.ItemDropTweaks;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Alle Einstellungen EINES Items - als echter YACL-Bildschirm.
 *
 * WARUM KEIN EIGENER BILDSCHIRM MIT EIGENEN REGLERN:
 * Ein nachgebauter Regler sieht nie ganz gleich aus, und bei jedem
 * YACL-Update waere er wieder einen Tick daneben. Hier werden statt dessen
 * drei gewoehnliche YACL-Optionen gebaut und YACL zeichnet sie selbst -
 * also sind es dieselben Regler wie in jeder anderen Zeile, nicht bloss
 * aehnliche.
 *
 * WAS HIER EINGESTELLT WIRD, GILT ALLEIN:
 * Die Werte ersetzen die allgemeinen Einstellungen, sie verrechnen sich
 * nicht mit ihnen. Darum starten neue Zeilen mit den allgemeinen Werten -
 * so aendert das blosse Hinzufuegen erst einmal nichts.
 *
 * ZUM SPEICHERN:
 * YACL ruft erst die Setter der Optionen und danach save. Deshalb stehen
 * in holder beim Speichern schon die neuen Werte. Das Item ist nicht
 * Teil davon: es wird direkt beim Auswaehlen gesetzt, weil YACLs
 * Speicherknopf von einer Aenderung nichts wuesste, die an keiner Option
 * haengt.
 */
public final class ItemSettingsScreen {

	private ItemSettingsScreen() {}

	/** Was der Aufrufer beim Speichern zurueckbekommt - ohne das Item. */
	public interface Sink {
		void apply(float scale, int glow, boolean shadow);
	}

	/** Zwischenlager fuer die drei Werte, solange der Bildschirm offen ist. */
	private static final class Holder {
		float scale;
		int glow;
		boolean shadow;

		Holder(ItemScale from) {
			this.scale = from.scale();
			this.glow = from.glow();
			this.shadow = from.shadow();
		}
	}

	/**
	 * @param parent  Bildschirm, zu dem der Zurueck-Knopf fuehrt
	 * @param value   die aktuellen Werte der Zeile
	 * @param onSave  wird beim Speichern mit den neuen Werten gerufen
	 * @param onItem  wird sofort gerufen, wenn ein anderes Item gewaehlt wird
	 * @param blocked Items, die eine andere Zeile schon belegt
	 */
	public static Screen create(Screen parent, ItemScale value,
	                            Sink onSave, Consumer<Item> onItem,
	                            Predicate<Item> blocked) {
		Holder holder = new Holder(value);

		return YetAnotherConfigLib.createBuilder()
			.title(name("item_scales"))
			.save(() -> onSave.apply(holder.scale, holder.glow, holder.shadow))
			.category(ConfigCategory.createBuilder()
				.name(name("item_scales"))

				// Item wechseln. Die Auswahl kehrt von selbst hierher
				// zurueck, darum hier kein setScreen danach.
				.option(ButtonOption.createBuilder()
					.name(name("item_change"))
					.description(describe("item_change"))
					.text(name("item_change.action"))
					.action(screen -> MinecraftClient.getInstance().setScreen(
						new ItemPickerScreen(screen, onItem, blocked)))
					.build())

				.option(Option.<Float>createBuilder()
					.name(name("scale"))
					.description(describe("scale"))
					.binding(1.0F, () -> holder.scale, v -> holder.scale = v)
					.controller(opt -> FloatSliderControllerBuilder.create(opt)
						.range(ItemDropTweaks.ITEM_MIN, ItemDropTweaks.ITEM_MAX)
						.step(0.05F))
					.build())

				.option(Option.<Integer>createBuilder()
					.name(name("glow"))
					.description(describe("glow"))
					.binding(0, () -> holder.glow, v -> holder.glow = v)
					.controller(opt -> IntegerSliderControllerBuilder.create(opt)
						.range(0, 15)
						.step(1))
					.build())

				.option(Option.<Boolean>createBuilder()
					.name(name("scale_shadow"))
					.description(describe("scale_shadow"))
					.binding(true, () -> holder.shadow, v -> holder.shadow = v)
					.controller(opt -> BooleanControllerBuilder.create(opt)
						.yesNoFormatter().coloured(true))
					.build())

				.build())
			.build()
			.generateScreen(parent);
	}

	private static Text name(String id) {
		return Text.translatable("itemdroptweaks.option." + id);
	}

	private static OptionDescription describe(String id) {
		return OptionDescription.createBuilder()
			.text(Text.translatable("itemdroptweaks.option." + id + ".desc"))
			.build();
	}
}
