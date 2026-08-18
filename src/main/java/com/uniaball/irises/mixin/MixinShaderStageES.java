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
 * Replace the GLSL source of Sodium's "clouds" shader program with an IrisES-owned,
 * #moj_import-free copy.
 *
 * <p>Root cause: Sodium 0.8.x ships its own core/clouds.{vsh,fsh} whose GLSL uses
 * {@code #moj_import <fog.glsl>}; on the DesktopGlues + Adreno stack the resulting
 * clouds program links with none of the vanilla uniforms, so clouds render with
 * zero matrices and are invisible. Shipping the same resources from IrisES does not
 * help because Fabric loads Sodium after IrisES, so Sodium's copy wins the resource
 * merge.
 *
 * <p>{@link ShaderStage#load} receives the raw shader file as an InputStream right
 * before preprocessing and compilation. Two programs are named "clouds" at runtime:
 * Iris's own CLOUDS program (ExtendedShader, whose source is Iris-transformed GLSL
 * without moj_import) and Sodium CloudRenderer's program (plain ShaderProgram, whose
 * source is the raw Sodium resource with moj_import). Instead of tracking the
 * caller, we inspect the stream content: only when it contains {@code #moj_import}
 * do we swap in our bundled copy.
 */
@Mixin(ShaderStage.class)
public abstract class MixinShaderStageES {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	@ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
	private static InputStream irises$replaceCloudsSource(InputStream source, ShaderStage.Type type, String name) {
		if (!"clouds".equals(name)) {
			return source;
		}

		try {
			byte[] raw = source.readAllBytes();
			String content = new String(raw, StandardCharsets.UTF_8);
			String firstLine = content.lines().findFirst().orElse("(empty)").trim();

			// Diagnostic: log every clouds shader that reaches ShaderStage.load so we
			// can see what source actually arrives (Sodium's moj_import copy vs our
			// bundled copy vs Iris-transformed GLSL).
			LOGGER.info("[IrisES] clouds load: type={} bytes={} hasMojImport={} firstLine='{}'",
				type, raw.length, content.contains("#moj_import"), firstLine);

			if (!content.contains("#moj_import")) {
				// Iris-transformed GLSL or some other already-valid source: pass through.
				return new ByteArrayInputStream(raw);
			}

			String path = type == ShaderStage.Type.VERTEX
				? "/assets/minecraft/shaders/core/clouds.vsh"
				: "/assets/minecraft/shaders/core/clouds.fsh";
			InputStream ours = MixinShaderStageES.class.getResourceAsStream(path);
			if (ours == null) {
				LOGGER.warn("[IrisES] could not find bundled resource {}", path);
				return new ByteArrayInputStream(raw);
			}

			LOGGER.info("[IrisES] replacing clouds {} shader (had #moj_import) with bundled {}", type, path);
			return ours;
		} catch (IOException e) {
			LOGGER.warn("[IrisES] failed to inspect clouds shader source", e);
			return source;
		}
	}
}
