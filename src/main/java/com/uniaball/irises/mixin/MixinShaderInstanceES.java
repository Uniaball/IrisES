package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safe (Java-only, no GL calls) diagnostic for the "clouds not rendering" issue.
 *
 * The clouds ShaderProgram built by Sodium 0.8.13's CloudRenderer links without the
 * six vanilla uniform names (ModelViewMat/ProjMat/ColorModulator/FogStart/FogEnd/FogColor),
 * so clouds render with zero matrices. The previous diagnostic that enumerated uniforms
 * via glGetActiveUniform crashed the Adreno driver (SIGSEGV in libGLESv2_adreno.so)
 * through the DesktopGlues layer, so we now only inspect Java-side state: the name of
 * the vertex/fragment ShaderStage tells us whether the linked GLSL is Sodium's own
 * core/clouds (name "clouds") or an Iris-transformed copy of gbuffers_clouds (any other
 * name), which decides the fix.
 */
@Mixin(ShaderProgram.class)
public abstract class MixinShaderInstanceES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	@Shadow
	@Final
	private int glRef;

	@Shadow
	@Final
	private ShaderStage vertexShader;

	@Shadow
	@Final
	private ShaderStage fragmentShader;

	@Unique
	private static int irises$cloudsSeen;

	/**
	 * Vanilla ShaderStage caches compiled stages per (type, name). Iris's own
	 * "clouds" ExtendedShader compiles first and caches its iris_-prefixed GLSL;
	 * Sodium's CloudRenderer program (a plain ShaderProgram) then reuses that
	 * cached stage, so none of its vanilla uniform names resolve. Bust the
	 * "clouds" cache entries before every clouds ShaderProgram construction so
	 * the program actually recompiles (and MixinShaderStageES can swap in our
	 * vanilla-named source for the Sodium one). Static handler at HEAD is fine
	 * (no access to 'this' before super()).
	 */
	@Inject(method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", at = @At("HEAD"))
	private static void irises$purgeCloudsCache(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
		if ("clouds".equals(name)) {
			for (ShaderStage.Type type : ShaderStage.Type.values()) {
				type.getLoadedShaders().remove("clouds");
			}
		}
	}

	@Inject(method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", at = @At("TAIL"))
	private void irises$inspectClouds(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
		if (!"clouds".equals(name)) {
			return;
		}

		String vsName = vertexShader == null ? "(null)" : ((ShaderStageAccessor) vertexShader).irises$getName();
		String fsName = fragmentShader == null ? "(null)" : ((ShaderStageAccessor) fragmentShader).irises$getName();

		// Log every distinct clouds instance: the concrete class tells us whether this
		// program belongs to Iris (ExtendedShader, silently swallows uniform warnings)
		// or to Sodium's CloudRenderer (plain ShaderProgram).
		irises$cloudsSeen++;
		LOGGER.info("[IrisES] clouds instance #{} linked. class={} vertexShader.name='{}' fragmentShader.name='{}' (program {})",
			irises$cloudsSeen, getClass().getName(), vsName, fsName, glRef);
	}
}
