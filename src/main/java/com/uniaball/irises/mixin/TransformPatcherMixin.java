package com.uniaball.irises.mixin;

import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the pack's own GLSL ES version declaration intact.
 * <p>
 * Iris unconditionally rewrites the version statement of every shader it
 * processes ({@code #version 320 es} becomes {@code #version 330 core} on
 * 1.8.x / {@code #version 410 core} on 1.7.x and {@code profile = Profile.CORE}
 * is forced, see TransformPatcher's else branch). The actual code transformation
 * is profile agnostic, so the only thing we need to undo is the version
 * statement: if the pack source declares an ES version, restore that exact
 * declaration in the patched output.
 * <p>
 * The GL implementation then receives {@code #version 320 es} untouched and
 * compiles it natively as GLSL ES 3.2 (DesktopGlues direct path, or desktop
 * GL 4.6 via ARB_ES3_2_compatibility).
 */
@Mixin(value = TransformPatcher.class, remap = false)
public class TransformPatcherMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	private static final Pattern ES_VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)\\s+es");
	private static final Pattern REWRITTEN_VERSION_PATTERN = Pattern.compile("(?m)^#version\\s+\\d{3}\\s+core");

	@Inject(method = "transform", at = @At("TAIL"), remap = false)
	private static void irises$restoreEsVersion(
		String name,
		String vertex,
		String geometry,
		String tessControl,
		String tessEval,
		String fragment,
		Parameters parameters,
		CallbackInfoReturnable<Map<PatchShaderType, String>> cir
	) {
		Map<PatchShaderType, String> result = cir.getReturnValue();
		if (result == null) {
			return;
		}

		String es = findEsVersion(vertex, geometry, tessControl, tessEval, fragment);
		if (es == null) {
			return;
		}

		Map<PatchShaderType, String> rewritten = new EnumMap<>(PatchShaderType.class);
		for (Map.Entry<PatchShaderType, String> entry : result.entrySet()) {
			rewritten.put(entry.getKey(),
				REWRITTEN_VERSION_PATTERN.matcher(entry.getValue()).replaceAll("#version " + es + " es"));
		}

		LOGGER.info("Restored ES version declaration (#version {} es) for program '{}'", es, name);
		cir.setReturnValue(rewritten);
	}

	private static String findEsVersion(String... sources) {
		for (String source : sources) {
			if (source == null) {
				continue;
			}
			Matcher matcher = ES_VERSION_PATTERN.matcher(source);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return null;
	}
}
