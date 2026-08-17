package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

/**
 * Diagnostic mixin for the "clouds not rendering" issue (Iris 1.8.14 + Sodium 0.8.13).
 *
 * Sodium 0.8.13 ships its own assets/minecraft/shaders/core/clouds.{vsh,fsh,json} which
 * override the vanilla resources. Its CloudRenderer builds a clouds ShaderProgram whose
 * clouds.json declares exactly ModelViewMat/ProjMat/ColorModulator/FogStart/FogEnd/FogColor,
 * yet at runtime the game logs "could not find uniform named ..." for all six, so clouds
 * render with zero matrices and are invisible.
 *
 * This mixin dumps the ACTUAL compiled source of the clouds program (via glGetShaderSource)
 * plus the six uniform locations, so we can tell whether the linked GLSL is Sodium's own
 * (vanilla uniform names) or an Iris-transformed copy (iris_ prefix). That decides the fix.
 */
@Mixin(ShaderProgram.class)
public abstract class MixinShaderInstanceES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	private static final String[] CLOUDS_UNIFORMS = {
		"ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogColor"
	};

	@Shadow
	@Final
	private int glRef;

	@Inject(method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", at = @At("TAIL"))
	private void irises$inspectClouds(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
		if (!"clouds".equals(name)) {
			return;
		}

		LOGGER.info("[IrisES] clouds program {} linked. Active uniforms (from GL):", glRef);
		int count = GL20C.glGetProgrami(glRef, GL20C.GL_ACTIVE_UNIFORMS);
		for (int i = 0; i < count; i++) {
			IntBuffer size = IntBuffer.allocate(1);
			IntBuffer type = IntBuffer.allocate(1);
			String uname = GL20C.glGetActiveUniform(glRef, i, 256, size, type);
			LOGGER.info("[IrisES]   uniform[{}] '{}' (size {}, type {})", i, uname, size.get(0), type.get(0));
		}

		LOGGER.info("[IrisES] clouds program {} uniform locations:", glRef);
		for (String u : CLOUDS_UNIFORMS) {
			int loc = GL20C.glGetUniformLocation(glRef, u);
			LOGGER.info("[IrisES]   uniform '{}' -> location {}", u, loc);
		}
	}
}
