package com.uniaball.irises.mixin;

import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Makes Iris's preprocessor aware of GLSL ES shaders.
 * <p>
 * JCPP evaluates {@code #ifdef GL_ES} against the environment defines it has
 * been told about, and {@code GL_ES} is not among them, so any ES-only
 * guarded code (most importantly the {@code precision} statement, which is
 * mandatory in ES fragment shaders but invalid in desktop GLSL) would be
 * stripped before the driver ever sees it. For packs that declare an ES
 * version, define {@code GL_ES} so those branches survive preprocessing.
 */
@Mixin(value = JcppProcessor.class, remap = false)
public class JcppProcessorMixin {
	private static final Pattern ES_VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)\\s+es");

	@ModifyVariable(method = "glslPreprocessSource", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
	private static Iterable<StringPair> irises$defineGles(
		String source,
		Iterable<StringPair> environmentDefines
	) {
		if (source == null || !ES_VERSION_PATTERN.matcher(source).find()) {
			return environmentDefines;
		}

		List<StringPair> defines = new ArrayList<>();
		for (StringPair define : environmentDefines) {
			if (define.key().equals("GL_ES")) {
				return environmentDefines;
			}
			defines.add(define);
		}
		defines.add(new StringPair("GL_ES", "1"));
		return defines;
	}
}
