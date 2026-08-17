package com.uniaball.irises.mixin;

import net.irisshaders.iris.pipeline.transform.Patch;
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
 * Keeps the pack's own GLSL ES version declaration intact and keeps the
 * transformed output compilable under ES semantics.
 * <p>
 * Iris rewrites the version statement of every shader it processes
 * ({@code #version 320 es} becomes {@code #version 330 core} on 1.8.x with
 * {@code profile = Profile.CORE} forced, see TransformPatcher's transform
 * logic). The actual code transformation is profile agnostic, so the only
 * thing we need to undo is the version statement: if the pack source declares
 * an ES version, restore that exact declaration in the patched output.
 * <p>
 * Only programs whose sources are exclusively the pack's own code are suitable
 * for ES compilation: composite, final, shadow and compute programs
 * (Patch.COMPOSITE / Patch.COMPUTE). Sodium and vanilla pipeline programs mix
 * the pack sources with desktop-GLSL injected by Iris (SodiumTransformer /
 * VanillaTransformer), which lacks precision declarations and uses uint
 * types, so they must keep the rewritten desktop version and compile as-is.
 * <p>
 * Two additional quirks are repaired here:
 * <ul>
 *   <li>Iris' isLines branch bumps the version number to 330 without touching
 *       the profile, turning {@code #version 320 es} into the invalid
 *       {@code #version 330 es} (ESSL only allows 100/300/310/320). Such
 *       outputs are repaired back to the pack's ES declaration so the program
 *       is handed to the driver as native ES rather than failing in
 *       DesktopGlues' GLSL-to-ESSL translation.</li>
 *   <li>CommonTransformer unconditionally injects a global initializer
 *       ({@code iris_FogParameters iris_Fog = iris_FogParameters(...)}),
 *       which is illegal under ES semantics ("Only consts can be used in a
 *       global initializer"). The initializer is stripped, leaving the plain
 *       declaration behind, so both the ES direct path and the desktop
 *       translation path keep working.</li>
 * </ul>
 * ES fragment shaders require default precision declarations before any use,
 * and Iris' transformers inject declarations without precision qualifiers, so
 * the restored ES header carries the default precision statements along.
 */
@Mixin(value = TransformPatcher.class, remap = false)
public class TransformPatcherMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("IrisES");

	private static final Pattern ES_VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)\\s+es");
	private static final Pattern REWRITTEN_VERSION_PATTERN = Pattern.compile("(?m)^#version\\s+\\d{3}\\s+(?:core|es)");
	private static final Pattern IRIS_FOG_INIT_PATTERN = Pattern.compile(
		"(?m)iris_FogParameters\\s+iris_Fog\\s*=\\s*iris_FogParameters\\s*\\([^;]*?\\)\\s*;");

	@Inject(method = "transform", at = @At("TAIL"), cancellable = true, remap = false)
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

		boolean patchEligible = parameters.patch == Patch.COMPOSITE || parameters.patch == Patch.COMPUTE;
		boolean hasInvalidEsProfile = false;
		for (String output : result.values()) {
			if (output != null && ES_VERSION_PATTERN.matcher(output).find()) {
				hasInvalidEsProfile = true;
				break;
			}
		}

		if (!patchEligible && !hasInvalidEsProfile) {
			return;
		}

		String esHeader = "#version " + es + " es\n"
			+ "precision highp float;\n"
			+ "precision highp int;\n"
			+ "precision highp sampler2D;\n"
			+ "precision highp sampler2DShadow;\n";

		Map<PatchShaderType, String> rewritten = new EnumMap<>(PatchShaderType.class);
		for (Map.Entry<PatchShaderType, String> entry : result.entrySet()) {
			String output = entry.getValue();
			if (output == null) {
				rewritten.put(entry.getKey(), null);
				continue;
			}

			output = IRIS_FOG_INIT_PATTERN.matcher(output).replaceAll("iris_FogParameters iris_Fog;");

			boolean restoreVersion = patchEligible || ES_VERSION_PATTERN.matcher(output).find();
			if (restoreVersion) {
				output = REWRITTEN_VERSION_PATTERN.matcher(output).replaceFirst(esHeader);
			}

			rewritten.put(entry.getKey(), output);
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