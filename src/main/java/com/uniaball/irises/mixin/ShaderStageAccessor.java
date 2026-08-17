package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@code name} field of ShaderStage (yarn mapping). A top-level
 * accessor mixin is required here: a nested interface inside a mixin on a
 * different target class gets mis-targeted by the mixin processor.
 */
@Mixin(ShaderStage.class)
public interface ShaderStageAccessor {
	@Accessor("name")
	String irises$getName();
}
