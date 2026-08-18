package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.InputStream;

/**
 * Replace the GLSL source of the "clouds" shader program with an IrisES-owned,
 * #moj_import-free copy.
 *
 * <p>Root cause: Sodium 0.8.13 ships its own core/clouds.{vsh,fsh,json} and its
 * GLSL uses {@code #moj_import <fog.glsl>}. On the DesktopGlues + Adreno stack the
 * resulting clouds program links with none of the six vanilla uniforms
 * (ModelViewMat/ProjMat/ColorModulator/FogStart/FogEnd/FogColor), so clouds render
 * with zero matrices and are invisible. Shipping the same resources from IrisES does
 * not help because Fabric loads Sodium after IrisES (irises only depends on iris), so
 * Sodium's copy wins the resource merge.
 *
 * <p>{@link ShaderStage#load} receives the raw shader file as an InputStream right
 * before preprocessing and compilation. For the "clouds" program we swap that stream
 * for our own bundled copy that has no moj_import and inlines the fog helper, so the
 * program compiles with all six uniforms present.
 */
@Mixin(ShaderStage.class)
public abstract class MixinShaderStageES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	@ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
	private static InputStream irises$replaceCloudsSource(InputStream source, ShaderStage.Type type, String name) {
		if (!"clouds".equals(name)) {
			return source;
		}

		String path = type == ShaderStage.Type.VERTEX
			? "/assets/minecraft/shaders/core/clouds.vsh"
			: "/assets/minecraft/shaders/core/clouds.fsh";

		InputStream ours = MixinShaderStageES.class.getResourceAsStream(path);
		if (ours == null) {
			LOGGER.warn("[IrisES] could not find bundled resource {}", path);
			return source;
		}

		LOGGER.info("[IrisES] replacing clouds {} shader source with bundled {}", type, path);
		return ours;
	}
}
