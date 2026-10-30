package com.lumitier.itemdroptweaks.mixin;

import com.lumitier.itemdroptweaks.ItemDropTweaks;
import com.lumitier.itemdroptweaks.duck.ItemCountHolder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eingriffe in den Vanilla-Renderer fallengelassener Items.
 *
 * Verifiziert gegen den disassemblierten 1.21.11-Code (Yarn 1.21.11+build.6).
 * Der relevante Vanilla-Ablauf:
 *
 *   render(state, matrices, queue, camera):            [Instanzmethode]
 *     matrices.push();
 *     float y = -box.minY + 0.0625F + sin(age / 10.0F + uniqueOffset) * 0.1F + 0.1F;
 *     matrices.translate(0, y, 0);
 *     matrices.multiply(rotation(ItemEntity.getRotation(age, uniqueOffset)));
 *     render(matrices, queue, state.light, state, random, box);   [statisch, 6 Args]
 *     matrices.pop();
 *     super.render(...);
 *
 *   renderStack(matrices, queue, light, state, random):
 *     int amount = state.renderedAmount;                 // 1..5
 *     irs.render(...);                                   // erste Kopie
 *     for (int i = 1; i &lt; amount; i++) {
 *         matrices.push();
 *         matrices.translate((rnd*2-1)*0.15F, (rnd*2-1)*0.15F, (rnd*2-1)*0.15F);
 *         irs.render(...);
 *         matrices.pop();
 *     }
 *
 *   ItemStackEntityRenderState.update(...):
 *     this.renderedAmount = getRenderedAmount(stack.getCount());
 *
 *   static int getRenderedAmount(int count):
 *     &lt;=1 -&gt; 1, &lt;=16 -&gt; 2, &lt;=32 -&gt; 3, &lt;=48 -&gt; 4, sonst 5    &lt;- Deckel
 *
 * WICHTIG - Bridge-Methoden: render und updateRenderState existieren
 * zusaetzlich als synthetische Bruecken von EntityRenderer. Deshalb ueberall
 * der volle Deskriptor; ein blosser Methodenname wuerde mehrdeutig matchen.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

	// ------------------------------------------------------------------
	// Items komplett ausblenden
	// ------------------------------------------------------------------

	@Inject(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void itemdroptweaks$maybeSkip(ItemEntityRenderState state, MatrixStack matrices,
	                                      OrderedRenderCommandQueue queue, CameraRenderState camera,
	                                      CallbackInfo ci) {
		if (ItemDropTweaks.enabled && !ItemDropTweaks.renderDrops) {
			ci.cancel();
		}
	}

	// ------------------------------------------------------------------
	// Echte Stapelgroesse mitfuehren, Vanillas Deckel ersetzen,
	// Schatten und Eigenleuchten
	// ------------------------------------------------------------------

	@Inject(
		method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
		at = @At("RETURN")
	)
	private void itemdroptweaks$updateState(ItemEntity entity, ItemEntityRenderState state,
	                                        float tickDelta, CallbackInfo ci) {
		int count = entity.getStack().getCount();
		((ItemCountHolder) state).itemdroptweaks$setCount(count);

		if (!ItemDropTweaks.enabled) {
			return;
		}

		if (ItemDropTweaks.copiesByCount) {
			state.renderedAmount = ItemDropTweaks.copiesFor(count);
		}

		if (ItemDropTweaks.scaleShadow) {
			state.shadowRadius *= ItemDropTweaks.totalScale(count);
		}

		int g = ItemDropTweaks.clampInt(ItemDropTweaks.glow, 0, 15);
		if (g > 0) {
			// Nie abdunkeln: nur anheben, wenn das Umgebungslicht niedriger ist.
			state.light = Math.max(state.light, LightmapTextureManager.pack(g, g));
		}
	}

	// ------------------------------------------------------------------
	// Groesse. Eigene push/pop-Klammer statt Inject in render(), damit der
	// Matrix-Stack garantiert ausbalanciert bleibt.
	// ------------------------------------------------------------------

	@Redirect(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"
		)
	)
	private void itemdroptweaks$scaled(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
	                                   ItemStackEntityRenderState state, Random random, Box box) {
		float f = ItemDropTweaks.enabled
			? ItemDropTweaks.totalScale(((ItemCountHolder) state).itemdroptweaks$getCount())
			: 1.0F;

		if (f == 1.0F) {
			ItemEntityRenderer.render(matrices, queue, light, state, random, box);
			return;
		}

		matrices.push();
		matrices.scale(f, f, f);
		ItemEntityRenderer.render(matrices, queue, light, state, random, box);
		matrices.pop();
	}

	// ------------------------------------------------------------------
	// Drehung. getRotation() liefert einen mit age wachsenden Winkel.
	// Faktor 0 friert die Drehung ein (alle Items zeigen dann gleich).
	// ------------------------------------------------------------------

	@Redirect(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F")
	)
	private float itemdroptweaks$spin(float age, float uniqueOffset) {
		float r = ItemEntity.getRotation(age, uniqueOffset);
		if (!ItemDropTweaks.enabled) {
			return r;
		}
		return r * ItemDropTweaks.clamp(ItemDropTweaks.spinSpeed, 0.0F, 5.0F);
	}

	// ------------------------------------------------------------------
	// Schweben: Vanilla rechnet sin(age / 10.0F + uniqueOffset) * 0.1F + 0.1F.
	// Groesserer Divisor = langsamer. Bei Tempo 0 bleibt sin() konstant,
	// jedes Item ruht dann auf seiner eigenen Hoehe statt alle auf derselben.
	// ------------------------------------------------------------------

	@ModifyConstant(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		constant = @Constant(floatValue = 10.0F)
	)
	private float itemdroptweaks$bobDivisor(float original) {
		if (!ItemDropTweaks.enabled) {
			return original;
		}
		float sp = ItemDropTweaks.clamp(ItemDropTweaks.bobSpeed, 0.0F, 5.0F);
		if (sp <= 0.001F) {
			return 1.0E9F;
		}
		return original / sp;
	}

	/**
	 * Schwebehoehe. 0.1F kommt in der Methode ZWEIMAL vor:
	 * einmal als Faktor auf sin(), einmal als fester Sockel.
	 * ordinal = 0 trifft den Faktor - das ist der Wert, der die Amplitude
	 * steuert. Trifft er stattdessen den Sockel, schwebt das Item hoeher
	 * statt staerker; dann gehoert hier ordinal = 1 hin.
	 */
	@ModifyConstant(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		constant = @Constant(floatValue = 0.1F, ordinal = 0)
	)
	private float itemdroptweaks$bobHeight(float original) {
		if (!ItemDropTweaks.enabled) {
			return original;
		}
		return original * ItemDropTweaks.clamp(ItemDropTweaks.bobHeight, 0.0F, 3.0F);
	}

	// ------------------------------------------------------------------
	// Streuung der Kopien im Haufen.
	//
	// ACHTUNG: Die gleichnamige Methode renderStack() enthaelt dieselbe Logik,
	// wird aus dem Item-Entity-Pfad aber NIE aufgerufen - sie ist ein
	// oeffentlicher Helfer fuer anderen Code. Ein Hook dort greift sauber und
	// bleibt trotzdem wirkungslos, ohne dass defaultRequire anschlaegt.
	// Das Ziel ist die 6-Arg-render(), die die Instanz-render() aufruft.
	//
	// 0.15F kommt dort fuenfmal vor: dreimal im Zweig fuer 3D-Modelle
	// (x, y, z Zufallsversatz) und zweimal im Zweig fuer flache Sprites.
	// Ohne ordinal werden alle fuenf getroffen - genau so gewollt, damit die
	// Option auch bei flachen Items wirkt.
	// ------------------------------------------------------------------

	@ModifyConstant(
		method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V",
		constant = @Constant(floatValue = 0.15F)
	)
	private static float itemdroptweaks$spread(float original) {
		if (!ItemDropTweaks.enabled) {
			return original;
		}
		return original * ItemDropTweaks.clamp(ItemDropTweaks.spread, 0.0F, 3.0F);
	}
}
