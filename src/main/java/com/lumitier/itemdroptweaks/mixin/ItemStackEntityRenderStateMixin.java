package com.lumitier.itemdroptweaks.mixin;

import com.lumitier.itemdroptweaks.duck.ItemCountHolder;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Haengt die echte Stapelgroesse an den RenderState.
 *
 * Reines Datenfeld, keine Injektion in Vanilla-Code - damit kollisionsfrei
 * gegenueber jeder anderen Mod, die denselben RenderState benutzt.
 */
@Mixin(ItemStackEntityRenderState.class)
public class ItemStackEntityRenderStateMixin implements ItemCountHolder {

	@Unique
	private int itemdroptweaks$count;

	@Override
	public int itemdroptweaks$getCount() {
		return this.itemdroptweaks$count;
	}

	@Override
	public void itemdroptweaks$setCount(int count) {
		this.itemdroptweaks$count = count;
	}
}
