package com.uniaball.irises;

/**
 * Tracks whether the ShaderProgram named "clouds" currently being constructed is
 * an Iris {@code ExtendedShader} (Iris's own CLOUDS program) or Sodium's plain
 * ShaderProgram. Plain class (not a mixin) so it can expose public static
 * helpers without tripping the mixin "no non-private @Unique static method"
 * rule.
 */
public final class CloudProgramTracker {
	private static final ThreadLocal<Boolean> IRIS_CLOUDS = new ThreadLocal<>();

	private CloudProgramTracker() {
	}

	public static void mark(boolean isIris) {
		IRIS_CLOUDS.set(isIris);
	}

	public static void clear() {
		IRIS_CLOUDS.remove();
	}

	public static boolean isIrisClouds() {
		return Boolean.TRUE.equals(IRIS_CLOUDS.get());
	}
}
