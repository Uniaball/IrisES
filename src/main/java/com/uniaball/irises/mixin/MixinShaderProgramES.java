package com.uniaball.irises.mixin;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks whether the ShaderProgram currently being constructed and named "clouds"
 * is an Iris {@code ExtendedShader} (Iris's own CLOUDS program, which must keep
 * the Iris-transformed GLSL and Sodium-packed vertex layout) or Sodium's plain
 * ShaderProgram (whose source we swap in {@link MixinShaderStageES}).
 *
 * <p>At runtime two programs are named "clouds": Iris's CLOUDS/CLOUDS_SODIUM
 * (ExtendedShader) and Sodium 0.8.x CloudRenderer's program (plain
 * ShaderProgram). Replacing the source of the Iris one breaks its vertex
 * binding, so the source swap must only apply to the Sodium one.
 */
@Mixin(ShaderProgram.class)
public abstract class MixinShaderProgramES {
	@Unique
	private static final ThreadLocal<Boolean> IRIS_CLOUDS = new ThreadLocal<>();

	@Inject(method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", at = @At("HEAD"))
	private void irises$markClouds(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
		if ("clouds".equals(name)) {
			IRIS_CLOUDS.set(getClass().getName().contains("ExtendedShader"));
		}
	}

	@Inject(method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", at = @At("RETURN"))
	private void irises$unmarkClouds(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
		if ("clouds".equals(name)) {
			IRIS_CLOUDS.remove();
		}
	}

	@Unique
	public static boolean isIrisClouds() {
		return Boolean.TRUE.equals(IRIS_CLOUDS.get());
	}
}
