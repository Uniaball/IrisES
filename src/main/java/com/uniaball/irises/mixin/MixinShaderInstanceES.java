package com.uniaball.irises.mixin;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic mixin for the "clouds not rendering" issue (Iris 1.8.14 + Sodium 0.8.13).
 *
 * Sodium 0.8.13 ships its own assets/minecraft/shaders/core/clouds.{vsh,fsh,json} which
 * override the vanilla resources. Its CloudRenderer builds a clouds ShaderInstance whose
 * clouds.json declares exactly ModelViewMat/ProjMat/ColorModulator/FogStart/FogEnd/FogColor,
 * yet at runtime the game logs "could not find uniform named ..." for all six, so clouds
 * render with zero matrices and are invisible.
 *
 * This mixin dumps the ACTUAL compiled source of the clouds program (via glGetShaderSource)
 * plus the six uniform locations, so we can tell whether the linked GLSL is Sodium's own
 * (vanilla uniform names) or an Iris-transformed copy (iris_ prefix). That decides the fix.
 */
@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstanceES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	private static final String[] CLOUDS_UNIFORMS = {
		"ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogColor"
	};

	@Shadow
	@Final
	private int programId;

	@Shadow
	@Final
	private Program vertexProgram;

	@Shadow
	@Final
	private Program fragmentProgram;

	@Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", at = @At("TAIL"))
	private void irises$inspectClouds(ResourceProvider provider, String name, VertexFormat format, CallbackInfo ci) {
		if (!"clouds".equals(name)) {
			return;
		}

		LOGGER.info("[IrisES] clouds program {} linked. Uniform locations:", programId);
		for (String u : CLOUDS_UNIFORMS) {
			int loc = GL20C.glGetUniformLocation(programId, u);
			LOGGER.info("[IrisES]   uniform '{}' -> location {}", u, loc);
		}

		int vsId = vertexProgram.getId();
		int fsId = fragmentProgram.getId();
		String vsh = GL20C.glGetShaderSource(vsId);
		String fsh = GL20C.glGetShaderSource(fsId);

		LOGGER.info("[IrisES] clouds vertex shader (id={}, {} lines):\n{}",
			vsId, vsh == null ? 0 : vsh.split("\n", -1).length, firstLines(vsh, 30));
		LOGGER.info("[IrisES] clouds fragment shader (id={}, {} lines):\n{}",
			fsId, fsh == null ? 0 : fsh.split("\n", -1).length, firstLines(fsh, 30));
	}

	private static String firstLines(String src, int n) {
		if (src == null) {
			return "(null)";
		}
		String[] lines = src.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(lines.length, n); i++) {
			sb.append(lines[i]).append('\n');
		}
		return sb.toString();
	}
}
