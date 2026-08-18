package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Give Sodium's "clouds" ShaderProgram a source that links with the vanilla
 * uniform names, without touching Iris's own CLOUDS programs.
 *
 * <p>Root cause chain (Iris 1.8.14 + Sodium 0.8.x):
 * <ol>
 *   <li>Iris routes every shader resource through its own factory, so the
 *       "clouds" program Sodium's CloudRenderer builds receives the
 *       Iris-transformed gbuffers_clouds GLSL ({@code #version 330 core},
 *       {@code iris_*} uniform names) instead of Sodium's raw core/clouds.</li>
 *   <li>Iris's own CLOUDS programs are {@code ExtendedShader}s, which get
 *       Iris's uniform renaming. Sodium's program is a plain ShaderProgram
 *       whose json (from Sodium's clouds.json) asks for the vanilla names
 *       (ModelViewMat/ProjMat/ColorModulator/FogStart/FogEnd/FogColor); none
 *       exist in the iris_-prefixed GLSL, so the program links "empty" and
 *       clouds render with zero matrices.</li>
 * </ol>
 *
 * <p>{@link ShaderStage#load} receives the raw GLSL InputStream right before
 * preprocessing and compilation. When the call originates from Sodium's
 * CloudRenderer (no {@code net.irisshaders.iris.*} frame in the stack), we
 * swap the stream for our bundled vanilla-named copy so all six uniforms are
 * found. Iris's ExtendedShader construction is left untouched.
 */
@Mixin(ShaderStage.class)
public abstract class MixinShaderStageES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	@ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
	private static InputStream irises$replaceCloudsSource(InputStream source, ShaderStage.Type type, String name) {
		if (!"clouds".equals(name)) {
			return source;
		}

		boolean irisCaller = false;
		for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
			if (e.getClassName().startsWith("net.irisshaders.iris.")) {
				irisCaller = true;
				break;
			}
		}

		try {
			byte[] raw = source.readAllBytes();
			String content = new String(raw, StandardCharsets.UTF_8);
			String firstLine = content.lines().findFirst().orElse("(empty)").trim();

			LOGGER.info("[IrisES] clouds load: type={} bytes={} irisCaller={} firstLine='{}'",
				type, raw.length, irisCaller, firstLine);

			if (irisCaller) {
				// Iris's own CLOUDS program: keep the Iris-transformed GLSL.
				return new ByteArrayInputStream(raw);
			}

			// Sodium's CloudRenderer program: swap in the vanilla-named copy so the
			// uniforms declared by Sodium's clouds.json actually resolve.
			String path = type == ShaderStage.Type.VERTEX
				? "/assets/minecraft/shaders/core/clouds.vsh"
				: "/assets/minecraft/shaders/core/clouds.fsh";
			InputStream ours = MixinShaderStageES.class.getResourceAsStream(path);
			if (ours == null) {
				LOGGER.warn("[IrisES] could not find bundled resource {}", path);
				return new ByteArrayInputStream(raw);
			}

			LOGGER.info("[IrisES] replacing clouds {} shader (sodium caller) with bundled {}", type, path);
			return ours;
		} catch (IOException e) {
			LOGGER.warn("[IrisES] failed to inspect clouds shader source", e);
			return source;
		}
	}
}
