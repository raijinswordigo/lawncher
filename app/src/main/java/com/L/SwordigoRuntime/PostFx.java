package com.L.SwordigoRuntime;

import android.opengl.GLES10;
import android.opengl.GLES11;
import android.opengl.GLES11Ext;
import android.util.Log;

import net.kiwi.lawncher.util.Prefs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * PostFx — fullscreen post-processing over the game's output, v3.
 *
 * v1 crashed: it called GLES20 entry points inside the GameView's GLES1 EGL
 * context (drivers abort). v2 was a pure GLES1 fixed-function rewrite but
 * still produced "flash then black / black frames" on some devices: it
 * committed three fragile primitives at once — RGB565 NPOT FBO colour
 * textures, the glDrawTexOES sprite-blit (whose results are undefined unless
 * PROJECTION/MODELVIEW are identity and which mishandles NPOT on several
 * drivers), and no error self-healing, so the first GL error permanently
 * poisoned the frame.
 *
 * v3 keeps the proven GLES1-only architecture (the game cannot run GLES2
 * shaders; libswordigo.so imports only GLES1 fixed-function entry points)
 * but replaces every fragile primitive:
 *
 *   1. FBO colour targets are RGBA8888 POT textures (RGB565 was the #1
 *      render-into-texture failure on mobile GLES1). Depth stays a
 *      POT-sized DEPTH_COMPONENT16_OES renderbuffer on the game target.
 *   2. Fullscreen blits use a plain client-array quad (glVertexPointer /
 *      glTexCoordPointer / glDrawArrays) in an explicit ortho window — no
 *      glDrawTexOES at all. UVs are explicit, so orientation is
 *      deterministic (see FLIP_V) and only the real viewport sub-rect of
 *      each POT is sampled.
 *   3. A capability gate (GL_OES_framebuffer_object + GL_OES_texture_npot +
 *      GLES1.1 for COMBINE) plus build-time framebuffer completeness checks.
 *      Any failure at build, OR an incomplete game target detected at frame
 *      time, self-disables PostFX for the life of the context and falls
 *      back to vanilla — PostFX can never leave the game black.
 *   4. The render graph is an explicit series of single-texture passes:
 *      copy / mask-multiply / INTERPOLATE grading / additive bloom /
 *      alpha-blended animated grain — the same operations the desktop
 *      pipeline's Tier-1 does, expressed as fixed-function ops.
 *
 * Pass graph per frame (all single texture unit):
 *   game_fbo (+depth)  <- the game draws here
 *     copy                  -> A
 *     mask (vignette *
 *           scanlines *
 *           warm/cool *
 *           brightness)     -> A
 *     sat  (INTERPOLATE
 *           -> mid-gray)    -> B
 *     contrast (INTERPOLATE
 *              -> mid-gray) -> B (or fb0 when no bloom)
 *     present               -> fb0
 *     bloom (additive: B
 *            downsampled to
 *            quarter res)   -> fb0
 *     grain (alpha-blended
 *            animated noise)-> fb0
 *
 * Presets: Off / Vibrant / Retro / Noir / Warm Film / Cinematic / CRT /
 * Atmospheric. Public API is unchanged (preset()/setPreset/enabled/bind/
 * apply/onContextLost) so SettingsScreen and GameRenderer keep working and
 * PRESET_COUNT grows automatically.
 */
public final class PostFx {

	private static final String TAG = "PostFx";
	private static final String PREF_PRESET = "postfx.preset";

	public static final int PRESET_OFF = 0;
	public static final int PRESET_VIBRANT = 1;
	public static final int PRESET_RETRO = 2;
	public static final int PRESET_NOIR = 3;
	public static final int PRESET_WARM = 4;
	public static final int PRESET_CINEMATIC = 5;
	public static final int PRESET_CRT = 6;
	public static final int PRESET_ATMOSPHERIC = 7;
	public static final int PRESET_COUNT = 8;
	public static final String[] PRESET_NAMES = {
			"Off", "Vibrant", "Retro", "Noir", "Warm Film", "Cinematic", "CRT", "Atmospheric"
	};

	/**
	 * {sat, contrast, brightness, vignette, scanlines, warm, grain, bloom}
	 * per preset. warm > 0 warms (boost r, cut b); warm < 0 cools.
	 */
	private static final float[][] PARAMS = {
			{1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f},                        // Off
			{1.18f, 1.06f, 0.02f, 0f, 0f, 0f, 0f, 0.02f},           // Vibrant
			{0.95f, 1.12f, 0.01f, 0.22f, 0.5f, 0.03f, 0.06f, 0f},   // Retro
			{0.10f, 1.20f, -0.03f, 0.42f, 0f, 0f, 0.16f, 0f},       // Noir
			{1.08f, 1f, 0.015f, 0.30f, 0f, 0.14f, 0.08f, 0.04f},    // Warm Film
			{0.72f, 1.18f, 0f, 0.46f, 0.14f, -0.07f, 0.12f, 0.05f}, // Cinematic
			{0.94f, 1.15f, 0f, 0.34f, 0.85f, 0.02f, 0.10f, 0f},     // CRT
			{0.82f, 0.94f, -0.01f, 0.30f, 0f, 0.06f, 0.22f, 0.06f}, // Atmospheric
	};

	// ------------------------------------------------------------------
	// GL objects
	// ------------------------------------------------------------------

	private static int fboGame;
	private static int texGame;
	private static int potWGame, potHGame;
	private static int rboDepth;
	private static int fboA;
	private static int texA;
	private static int potWA, potHA;
	private static int fboB;
	private static int texB;
	private static int potWB, potHB;
	private static int fboT;
	private static int texT;
	private static int potWT, potHT;
	private static int maskTex;
	private static int noiseTex1;
	private static int noiseTex2;
	/** Alternates 0/1 each frame so grain temporally sparkles. */
	private static int grainPhase;

	private static final int MASK_SIZE = 512;   // POT, cheap 1 MB RGBA
	private static final int NOISE_SIZE = 64;   // POT, 2x 16 KB

	private static int fw;
	private static int fh;
	private static boolean ready;
	private static boolean supported = true;
	private static boolean combineOk;           // GLES1.1 COMBINE/INTERPOLATE available
	private static int maskPreset = -1;

	/** Flip the frame vertically on the final present. Kept false to match the
	 *  OES_FBO convention (texture v=0 is the framebuffer's bottom row). Set
	 *  true only if the output appears upside-down on a specific device. */
	private static final boolean FLIP_V = false;

	// ------------------------------------------------------------------
	// Static fullscreen quad (reused per draw; single GL thread)
	// ------------------------------------------------------------------

	private static final float[] quadXY = new float[8];
	private static final float[] quadUV = new float[8];
	private static final FloatBuffer bufXY =
			ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	private static final FloatBuffer bufUV =
			ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

	// ------------------------------------------------------------------
	// Saved GL state
	// ------------------------------------------------------------------

	private static final int[] stViewport = new int[4];
	private static final int[] stInt = new int[4];
	private static final int[] stColorMask = new int[4];
	private static final float[] stColor4 = new float[4];
	private static final float[] stClear4 = new float[4];
	private static final float[] stAlphaRef = new float[1];
	private static boolean stDepth, stBlend, stScissor, stCull, stStencil, stAlphaTest;
	private static boolean stVertexArray, stTexCoordArray;
	private static boolean stTex0Enabled, stTex1Enabled;
	private static int stBoundTex0, stBoundTex1;
	private static int stActiveTex, stMatrixMode, stBlendSrc, stBlendDst;
	private static int stAlphaFunc, stDepthMask;

	private PostFx() {}

	// ------------------------------------------------------------------
	// Config (public API unchanged — SettingsScreen + GameRenderer use this)
	// ------------------------------------------------------------------

	public static int preset() {
		int p = Prefs.getInt(PREF_PRESET, PRESET_OFF);
		return (p >= 0 && p < PRESET_COUNT) ? p : PRESET_OFF;
	}

	public static void setPreset(int preset) {
		Prefs.putInt(PREF_PRESET, (preset >= 0 && preset < PRESET_COUNT) ? preset : PRESET_OFF);
	}

	public static boolean enabled() {
		return preset() != PRESET_OFF;
	}

	/** Call when the GL context is (re)created — GL objects die with it. */
	public static void onContextLost() {
		destroy();
	}

	// ------------------------------------------------------------------
	// Frame — bind the game target, then replay through the render graph
	// ------------------------------------------------------------------

	/** Bind the host FBO before the game draws. Only called when enabled. */
	public static void bind(int w, int h) {
		if (w <= 0 || h <= 0) return;
		ensure(w, h);
		if (!ready) return;

		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fboGame);
		GLES10.glViewport(0, 0, w, h);
		GLES10.glClearColor(0f, 0f, 0f, 1f);
		GLES10.glClear(GLES10.GL_COLOR_BUFFER_BIT | GLES10.GL_DEPTH_BUFFER_BIT);
	}

	/** Replay the frame through the active preset's passes into the default framebuffer. */
	public static void apply(int w, int h) {
		if (!ready || !enabled()) return;
		if (w <= 0 || h <= 0) return;
		// Safety net: if the game target went bad between frames, fall back to
		// vanilla rather than sampling a dead texture (which would read black).
		if (GLES11Ext.glCheckFramebufferStatusOES(GLES11Ext.GL_FRAMEBUFFER_OES)
				!= GLES11Ext.GL_FRAMEBUFFER_COMPLETE_OES) {
			Log.e(TAG, "game FBO incomplete at frame time — postfx self-disabled");
			GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, 0);
			hardDisable();
			return;
		}

		try {
			int p = preset();
			float[] q = PARAMS[p];
			boolean maskOn = q[3] > 0.001f || q[4] > 0.001f || Math.abs(q[5]) > 0.001f
					|| Math.abs(q[2]) > 0.001f;
			boolean satOn = Math.abs(q[0] - 1f) > 0.001f && combineOk;
			boolean conOn = Math.abs(q[1] - 1f) > 0.001f && combineOk;
			boolean bloomOn = q[7] > 0.003f;
			boolean grainOn = q[6] > 0.002f;

			saveState();
			begin(w, h);

			// Re-bake the mask only when the preset changed.
			if (p != maskPreset) {
				buildMaskTexture(q);
				maskPreset = p;
			}
			grainPhase ^= 1; // animate grain every frame

			int src = texGame;

			if (maskOn) {
				copyPass(src, fboA, potWGame, potHGame, w, h);
				maskOverlayPass(fboA, w, h);
				src = texA;
			}

			if (satOn) {
				gradePass(src, fboB, potOf(src), potBOf(src), 1f - q[0], w, h);
				src = texB;
			}

			if (bloomOn) {
				// Graded image must live in B for both the present and the
				// bloom downsample to read.
				if (!satOn) copyPass(src, fboB, potOf(src), potBOf(src), w, h);
				if (conOn) {
					gradePass(src, fboB, potOf(src), potBOf(src), 1f - q[1], w, h);
				}
				// Present the graded result, then lay the soft glow on top.
				copyPass(texB, 0, potWB, potHB, w, h);
				bloomPass(texB, potWB, potHB, w, h, q[7]);
			} else {
				if (conOn) {
					gradePass(src, 0, potOf(src), potBOf(src), 1f - q[1], w, h);
				} else {
					copyPass(src, 0, potOf(src), potBOf(src), w, h);
				}
			}

			if (grainOn) {
				grainPass(w, h, q[6]);
			}

			restoreState();
		} catch (Throwable t) {
			Log.e(TAG, "postfx pass failed: " + t);
			GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, 0);
			destroy();
			supported = true; // transient — allow a clean retry on the next frame
		}
	}

	/** Correct source dims for the given in-flight source texture. */
	private static int potOf(int srcTex) {
		return srcTex == texGame ? potWGame : srcTex == texA ? potWA : potWB;
	}

	private static int potBOf(int srcTex) {
		return srcTex == texGame ? potHGame : srcTex == texA ? potHA : potHB;
	}

	// ------------------------------------------------------------------
	// Render graph passes (each binds its own target framebuffer)
	// ------------------------------------------------------------------

	/**
	 * Copy `srcTex` (POT tw×th, viewport sub-rect w×h) into `targetFbo`
	 * (REPLACE-equivalent, blend off; target 0 = default framebuffer).
	 * Binding the TARGET framebuffer before the source texture is deliberate:
	 * sampling a texture attached to the CURRENT draw target is framebuffer
	 * feedback (undefined).
	 */
	private static void copyPass(int srcTex, int targetFbo, int tw, int th, int w, int h) {
		bindTarget(targetFbo, w, h);
		setEnvModulate();
		GLES10.glColor4f(1f, 1f, 1f, 1f);
		glBlendOff();
		drawFullscreen(srcTex, tw, th, w, h);
	}

	/** Multiply the target framebuffer's content by the mask texture. */
	private static void maskOverlayPass(int targetFbo, int w, int h) {
		bindTarget(targetFbo, w, h);
		setEnvModulate();
		GLES10.glColor4f(1f, 1f, 1f, 1f);
		// dst' = 0*src + srcColor*dst  =>  dst *= mask
		GLES10.glBlendFunc(GLES10.GL_ZERO, GLES10.GL_SRC_COLOR);
		GLES10.glEnable(GLES10.GL_BLEND);
		drawFullscreen(maskTex, MASK_SIZE, MASK_SIZE, w, h);
		GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ZERO);
	}

	/**
	 * Grade pass into `targetFbo`: c' = A*(1-t) + B*t with A=src texture,
	 * B=mid-gray primary color, t the fade constant. Used for both saturation
	 * (t=1-sat) and contrast (t=1-contrast).
	 */
	private static void gradePass(int srcTex, int targetFbo, int tw, int th,
			float t, int w, int h) {
		bindTarget(targetFbo, w, h);
		setEnvInterpolate(t);
		GLES10.glColor4f(0.5f, 0.5f, 0.5f, 1f); // mid-gray via GL_PRIMARY_COLOR
		glBlendOff();
		drawFullscreen(srcTex, tw, th, w, h);
	}

	/**
	 * Soft glow: downsample `srcTex` into the quarter-res target with LINEAR,
	 * then add the upscaled softened copy over the (already presented) frame.
	 */
	private static void bloomPass(int srcTex, int tw, int th, int w, int h, float intensity) {
		// Downsample the full frame into the quarter target.
		bindTarget(fboT, Math.max(1, w / 4), Math.max(1, h / 4));
		setEnvModulate();
		GLES10.glColor4f(1f, 1f, 1f, 1f);
		glBlendOff();
		drawFullscreenTo(srcTex, tw, th, w, h, w / 4, h / 4);

		// Upscale-layered additive glow over the default framebuffer.
		bindTarget(0, w, h);
		setEnvModulate();
		GLES10.glColor4f(intensity, intensity, intensity, 1f);
		GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ONE);
		GLES10.glEnable(GLES10.GL_BLEND);
		drawFullscreen(texT, potWT, potHT, w, h);
		GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ZERO);
	}

	/** Alpha-blended animated grain (RGB ~ mid-gray, alpha = strength). */
	private static void grainPass(int w, int h, float strength) {
		setEnvModulate();
		GLES10.glColor4f(1f, 1f, 1f, strength);
		GLES10.glBlendFunc(GLES10.GL_SRC_ALPHA, GLES10.GL_ONE_MINUS_SRC_ALPHA);
		GLES10.glEnable(GLES10.GL_BLEND);
		drawFullscreen(grainPhase == 0 ? noiseTex1 : noiseTex2,
				NOISE_SIZE, NOISE_SIZE, w, h);
		GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ZERO);
	}

	/** Bind a render target (0 = default framebuffer) and set its viewport. */
	private static void bindTarget(int targetFbo, int vw, int vh) {
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, targetFbo);
		if (vw > 0 && vh > 0) {
			GLES10.glViewport(0, 0, vw, vh);
		}
	}

	private static void glBlendOff() {
		GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ZERO);
	}

	// ------------------------------------------------------------------
	// Fullscreen quad (client arrays — deterministic, no glDrawTexOES)
	// ------------------------------------------------------------------

	/** Draw `srcTex`, sampling its w×h sub-rect, mapped over w×h of the target. */
	private static void drawFullscreen(int srcTex, int tw, int th, int w, int h) {
		drawFullscreenTo(srcTex, tw, th, w, h, w, h);
	}

	/**
	 * Draw `srcTex` (POT tw×th) sampling its srcW×srcH sub-rect into drawW×drawH
	 * of the currently bound framebuffer's viewport. The single place where
	 * POT sub-rect sampling and y-orientation are decided.
	 */
	private static void drawFullscreenTo(int srcTex, int tw, int th,
			int srcW, int srcH, int drawW, int drawH) {
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
		GLES10.glEnable(GLES10.GL_TEXTURE_2D);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, srcTex);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_LINEAR);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_LINEAR);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_S, GLES10.GL_CLAMP_TO_EDGE);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_T, GLES10.GL_CLAMP_TO_EDGE);
		// Neutralise unit1 so only unit0's stage contributes.
		GLES10.glActiveTexture(GLES10.GL_TEXTURE1);
		GLES10.glDisable(GLES10.GL_TEXTURE_2D);
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);

		// Window-space ortho maps the quad 1:1 in pixels.
		GLES10.glMatrixMode(GLES10.GL_PROJECTION);
		GLES10.glPushMatrix();
		GLES10.glLoadIdentity();
		GLES10.glOrthof(0f, drawW, 0f, drawH, -1f, 1f);
		GLES10.glMatrixMode(GLES10.GL_MODELVIEW);
		GLES10.glPushMatrix();
		GLES10.glLoadIdentity();

		float uMax = srcW / (float) tw;
		float vMax = srcH / (float) th;
		if (uMax > 1f) uMax = 1f;
		if (vMax > 1f) vMax = 1f;
		float v0 = FLIP_V ? vMax : 0f;
		float v1 = FLIP_V ? 0f : vMax;
		float wf = drawW;
		float hf = drawH;

		quadXY[0] = 0f;    quadXY[1] = 0f;
		quadXY[2] = wf;    quadXY[3] = 0f;
		quadXY[4] = 0f;    quadXY[5] = hf;
		quadXY[6] = wf;    quadXY[7] = hf;
		quadUV[0] = 0f;        quadUV[1] = v0;
		quadUV[2] = uMax;      quadUV[3] = v0;
		quadUV[4] = 0f;        quadUV[5] = v1;
		quadUV[6] = uMax;      quadUV[7] = v1;

		bufXY.clear(); bufXY.put(quadXY).flip();
		bufUV.clear(); bufUV.put(quadUV).flip();

		GLES10.glEnableClientState(GLES10.GL_VERTEX_ARRAY);
		GLES10.glEnableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);
		GLES10.glVertexPointer(2, GLES10.GL_FLOAT, 0, bufXY);
		GLES10.glTexCoordPointer(2, GLES10.GL_FLOAT, 0, bufUV);
		GLES10.glDrawArrays(GLES10.GL_TRIANGLE_STRIP, 0, 4);
		GLES10.glDisableClientState(GLES10.GL_VERTEX_ARRAY);
		GLES10.glDisableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);

		GLES10.glMatrixMode(GLES10.GL_MODELVIEW);
		GLES10.glPopMatrix();
		GLES10.glMatrixMode(GLES10.GL_PROJECTION);
		GLES10.glPopMatrix();
	}

	/** Texenv: default GL_MODULATE (texture x primary color). */
	private static void setEnvModulate() {
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES10.GL_TEXTURE_ENV_MODE,
				new float[]{GLES10.GL_MODULATE}, 0);
	}

	/** Texenv: RGB=GL_INTERPOLATE(texture, primary, constant-t). */
	private static void setEnvInterpolate(float t) {
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES10.GL_TEXTURE_ENV_MODE,
				new float[]{GLES11.GL_COMBINE}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_COMBINE_RGB,
				new float[]{GLES11.GL_INTERPOLATE}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_SRC0_RGB,
				new float[]{GLES11.GL_TEXTURE}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_SRC1_RGB,
				new float[]{GLES11.GL_PRIMARY_COLOR}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_SRC2_RGB,
				new float[]{GLES11.GL_CONSTANT}, 0);
		// Alpha: keep the source's alpha untouched.
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_COMBINE_ALPHA,
				new float[]{GLES11.GL_REPLACE}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES11.GL_SRC0_ALPHA,
				new float[]{GLES11.GL_TEXTURE}, 0);
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES10.GL_TEXTURE_ENV_COLOR,
				new float[]{t, t, t, 1f}, 0);
	}

	/** Common pre-pass state: depth/blend/scissor/cull/stencil off, alpha ALWAYS. */
	private static void begin(int w, int h) {
		GLES10.glDisable(GLES10.GL_DEPTH_TEST);
		GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glDisable(GLES10.GL_SCISSOR_TEST);
		GLES10.glDisable(GLES10.GL_CULL_FACE);
		GLES10.glDisable(GLES10.GL_STENCIL_TEST);
		GLES10.glColorMask(true, true, true, true);
		// Never let an enabled alpha test cull the fullscreen quads.
		GLES10.glAlphaFunc(GLES10.GL_ALWAYS, 0f);
		GLES10.glDepthMask(true);
		GLES10.glViewport(0, 0, w, h);
	}

	// ------------------------------------------------------------------
	// State save / restore
	// ------------------------------------------------------------------

	private static void saveState() {
		stDepth = GLES11.glIsEnabled(GLES10.GL_DEPTH_TEST);
		stBlend = GLES11.glIsEnabled(GLES10.GL_BLEND);
		stScissor = GLES11.glIsEnabled(GLES10.GL_SCISSOR_TEST);
		stCull = GLES11.glIsEnabled(GLES10.GL_CULL_FACE);
		stStencil = GLES11.glIsEnabled(GLES10.GL_STENCIL_TEST);
		stAlphaTest = GLES11.glIsEnabled(GLES10.GL_ALPHA_TEST);
		stVertexArray = GLES11.glIsEnabled(GLES10.GL_VERTEX_ARRAY);
		stTexCoordArray = GLES11.glIsEnabled(GLES10.GL_TEXTURE_COORD_ARRAY);

		GLES10.glGetIntegerv(GLES11.GL_VIEWPORT, stViewport, 0);
		GLES10.glGetIntegerv(GLES11.GL_ACTIVE_TEXTURE, stInt, 0);
		stActiveTex = stInt[0];
		GLES10.glGetIntegerv(GLES11.GL_MATRIX_MODE, stInt, 0);
		stMatrixMode = stInt[0];
		GLES10.glGetIntegerv(GLES11.GL_BLEND_SRC, stInt, 0);
		stBlendSrc = stInt[0];
		GLES10.glGetIntegerv(GLES11.GL_BLEND_DST, stInt, 0);
		stBlendDst = stInt[0];
		GLES10.glGetIntegerv(GLES11.GL_ALPHA_TEST_FUNC, stInt, 0);
		stAlphaFunc = stInt[0];
		GLES11.glGetFloatv(GLES11.GL_ALPHA_TEST_REF, stAlphaRef, 0);
		GLES10.glGetIntegerv(GLES11.GL_DEPTH_WRITEMASK, stInt, 0);
		stDepthMask = stInt[0];
		GLES10.glGetIntegerv(GLES11.GL_COLOR_WRITEMASK, stColorMask, 0);

		GLES11.glGetFloatv(GLES11.GL_CURRENT_COLOR, stColor4, 0);
		GLES11.glGetFloatv(GLES11.GL_COLOR_CLEAR_VALUE, stClear4, 0);

		// Unit0 texture enable + binding.
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
		stTex0Enabled = GLES11.glIsEnabled(GLES10.GL_TEXTURE_2D);
		GLES10.glGetIntegerv(GLES11.GL_TEXTURE_BINDING_2D, stInt, 0);
		stBoundTex0 = stInt[0];

		// Unit1 texture enable + binding.
		GLES10.glActiveTexture(GLES10.GL_TEXTURE1);
		stTex1Enabled = GLES11.glIsEnabled(GLES10.GL_TEXTURE_2D);
		GLES10.glGetIntegerv(GLES11.GL_TEXTURE_BINDING_2D, stInt, 0);
		stBoundTex1 = stInt[0];
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
	}

	private static void restoreState() {
		// Unit0 env back to the GL default (texenv isn't queryable in the
		// Java ES1 wrappers, so always reset to MODULATE + white).
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
		setEnvModulate();
		GLES10.glTexEnvfv(GLES10.GL_TEXTURE_ENV, GLES10.GL_TEXTURE_ENV_COLOR,
				new float[]{1f, 1f, 1f, 1f}, 0);

		// Texture enables + bindings per unit.
		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
		if (stTex0Enabled) GLES10.glEnable(GLES10.GL_TEXTURE_2D);
		else GLES10.glDisable(GLES10.GL_TEXTURE_2D);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, stBoundTex0);

		GLES10.glActiveTexture(GLES10.GL_TEXTURE1);
		if (stTex1Enabled) GLES10.glEnable(GLES10.GL_TEXTURE_2D);
		else GLES10.glDisable(GLES10.GL_TEXTURE_2D);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, stBoundTex1);

		GLES10.glActiveTexture(stActiveTex);

		// Client array enables.
		if (stVertexArray) GLES10.glEnableClientState(GLES10.GL_VERTEX_ARRAY);
		else GLES10.glDisableClientState(GLES10.GL_VERTEX_ARRAY);
		if (stTexCoordArray) GLES10.glEnableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);
		else GLES10.glDisableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);

		// Enable flags + per-op settings.
		if (stBlend) GLES10.glEnable(GLES10.GL_BLEND);
		else GLES10.glDisable(GLES10.GL_BLEND);
		GLES10.glBlendFunc(stBlendSrc, stBlendDst);
		if (stDepth) GLES10.glEnable(GLES10.GL_DEPTH_TEST);
		else GLES10.glDisable(GLES10.GL_DEPTH_TEST);
		if (stScissor) GLES10.glEnable(GLES10.GL_SCISSOR_TEST);
		else GLES10.glDisable(GLES10.GL_SCISSOR_TEST);
		if (stCull) GLES10.glEnable(GLES10.GL_CULL_FACE);
		else GLES10.glDisable(GLES10.GL_CULL_FACE);
		if (stStencil) GLES10.glEnable(GLES10.GL_STENCIL_TEST);
		else GLES10.glDisable(GLES10.GL_STENCIL_TEST);
		if (stAlphaTest) GLES10.glEnable(GLES10.GL_ALPHA_TEST);
		else GLES10.glDisable(GLES10.GL_ALPHA_TEST);
		GLES10.glAlphaFunc(stAlphaFunc, stAlphaRef[0]);
		GLES10.glDepthMask(stDepthMask != 0);
		GLES10.glColorMask(stColorMask[0] != 0, stColorMask[1] != 0,
				stColorMask[2] != 0, stColorMask[3] != 0);

		// Colors + viewport.
		GLES10.glColor4f(stColor4[0], stColor4[1], stColor4[2], stColor4[3]);
		GLES10.glClearColor(stClear4[0], stClear4[1], stClear4[2], stClear4[3]);
		GLES10.glViewport(stViewport[0], stViewport[1], stViewport[2], stViewport[3]);

		// Always end with the default framebuffer bound; bind() re-binds
		// the game target on the next frame.
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, 0);
	}

	// ------------------------------------------------------------------
	// Setup / teardown
	// ------------------------------------------------------------------

	private static void ensure(int w, int h) {
		if (ready && fw == w && fh == h) return;
		destroy();
		fw = w;
		fh = h;
		try {
			buildPipeline(w, h);
		} catch (Throwable t) {
			Log.e(TAG, "postfx setup failed — disabling: " + t);
			destroy();
			supported = false; // do not retry every frame
		}
	}

	/** Permanently disable for the life of this GL context (+ drain errors). */
	private static void hardDisable() {
		GLES10.glGetError(); // drain any pending error so the game sees clean state
		destroy();
		supported = false;
	}

	private static void buildPipeline(int w, int h) {
		if (!checkCapabilities()) return; // ready stays false -> vanilla

		int[] tmp = new int[1];

		// Max supported texture dimension (POT cap for the targets).
		GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, tmp, 0);
		int maxTex = tmp[0] > 0 ? tmp[0] : 2048;

		potWGame = potFor(w, maxTex);
		potHGame = potFor(h, maxTex);
		if (potWGame < w || potHGame < h) {
			Log.e(TAG, "surface " + w + "x" + h + " exceeds GL_MAX_TEXTURE_SIZE "
					+ maxTex + " — postfx disabled");
			return;
		}

		texGame = makeColorTexture(potWGame, potHGame);
		texA = makeColorTexture(potWGame, potHGame);
		texB = makeColorTexture(potWGame, potHGame);
		potWA = potWGame;
		potHA = potHGame;
		potWB = potWGame;
		potHB = potHGame;

		int qw = potFor(Math.max(1, w / 4), maxTex);
		int qh = potFor(Math.max(1, h / 4), maxTex);
		texT = makeColorTexture(qw, qh);
		potWT = qw;
		potHT = qh;

		fboGame = makeFbo();
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fboGame);
		attachColor(fboGame, texGame);
		rboDepth = makeDepthBuffer(potWGame, potHGame);

		fboA = makeFbo();
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fboA);
		attachColor(fboA, texA);

		fboB = makeFbo();
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fboB);
		attachColor(fboB, texB);

		fboT = makeFbo();
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fboT);
		attachColor(fboT, texT);

		maskTex = makeColorTexture(MASK_SIZE, MASK_SIZE);
		noiseTex1 = makeColorTexture(NOISE_SIZE, NOISE_SIZE);
		noiseTex2 = makeColorTexture(NOISE_SIZE, NOISE_SIZE);
		bakeNoiseTexture(noiseTex1, 7);
		bakeNoiseTexture(noiseTex2, 31);
		grainPhase = 0;

		boolean complete = checkFramebuffer(fboGame) && checkFramebuffer(fboA)
				&& checkFramebuffer(fboB) && checkFramebuffer(fboT);
		if (!complete) {
			Log.e(TAG, "FBO incomplete — postfx disabled");
			destroy();
			return;
		}

		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, 0);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, 0);

		GLES10.glGetError(); // drain
		maskPreset = -1;
		ready = true;
		Log.i(TAG, "postfx ready @ " + w + "x" + h
				+ (combineOk ? " (GLES1.1 combine grading)" : " (no combine — no sat/contrast)")
				+ " targets " + potWGame + "x" + potHGame);
	}

	private static int potFor(int dim, int maxTex) {
		int p = 1;
		while (p < dim && p < maxTex) p <<= 1;
		return p;
	}

	private static int makeColorTexture(int tw, int th) {
		int[] tmp = new int[1];
		GLES10.glGenTextures(1, tmp, 0);
		int tex = tmp[0];
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, tex);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_LINEAR);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_LINEAR);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_S, GLES10.GL_CLAMP_TO_EDGE);
		GLES11.glTexParameteri(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_T, GLES10.GL_CLAMP_TO_EDGE);
		// RGBA8888: the renderable colour format every GLES1 FBO driver accepts
		// for sampling (RGB565 NPOT + glDrawTexOES was the black-screen path).
		GLES10.glTexImage2D(GLES10.GL_TEXTURE_2D, 0, GLES10.GL_RGBA, tw, th, 0,
				GLES10.GL_RGBA, GLES10.GL_UNSIGNED_BYTE, null);
		return tex;
	}

	private static int makeFbo() {
		int[] tmp = new int[1];
		GLES11Ext.glGenFramebuffersOES(1, tmp, 0);
		return tmp[0];
	}

	private static void attachColor(int fbo, int tex) {
		GLES11Ext.glFramebufferTexture2DOES(GLES11Ext.GL_FRAMEBUFFER_OES,
				GLES11Ext.GL_COLOR_ATTACHMENT0_OES, GLES10.GL_TEXTURE_2D, tex, 0);
	}

	private static int makeDepthBuffer(int tw, int th) {
		int[] tmp = new int[1];
		GLES11Ext.glGenRenderbuffersOES(1, tmp, 0);
		int rbo = tmp[0];
		GLES11Ext.glBindRenderbufferOES(GLES11Ext.GL_RENDERBUFFER_OES, rbo);
		GLES11Ext.glRenderbufferStorageOES(GLES11Ext.GL_RENDERBUFFER_OES,
				GLES11Ext.GL_DEPTH_COMPONENT16_OES, tw, th);
		// Depth must exactly match the colour attachment's POT size.
		GLES11Ext.glFramebufferRenderbufferOES(GLES11Ext.GL_FRAMEBUFFER_OES,
				GLES11Ext.GL_DEPTH_ATTACHMENT_OES, GLES11Ext.GL_RENDERBUFFER_OES, rbo);
		return rbo;
	}

	private static boolean checkFramebuffer(int fbo) {
		GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, fbo);
		return GLES11Ext.glCheckFramebufferStatusOES(GLES11Ext.GL_FRAMEBUFFER_OES)
				== GLES11Ext.GL_FRAMEBUFFER_COMPLETE_OES;
	}

	/**
	 * Requires GL_OES_framebuffer_object, GL_OES_texture_npot and — for the
	 * sat/contrast grade — a GLES1.1 context (COMBINE/INTERPOLATE). Without
	 * any of the former PostFX self-disables; without 1.1 specifically it
	 * still runs the mask / bloom / grain passes.
	 */
	private static boolean checkCapabilities() {
		if (!supported) return false;
		String exts = GLES10.glGetString(GLES10.GL_EXTENSIONS);
		if (exts == null || !exts.contains("GL_OES_framebuffer_object")
				|| !exts.contains("GL_OES_texture_npot")) {
			supported = false;
			Log.w(TAG, "missing required GL_OES extensions — postfx unavailable, game runs vanilla");
			return false;
		}
		String ver = GLES10.glGetString(GLES10.GL_VERSION);
		combineOk = ver != null && (ver.contains("1.1") || ver.contains("1.2") || ver.contains("2.0"));
		return true;
	}

	private static void bakeNoiseTexture(int tex, long seed) {
		Random rnd = new Random(seed);
		byte[] px = new byte[NOISE_SIZE * NOISE_SIZE * 4];
		for (int i = 0; i < px.length; i += 4) {
			float v = 0.5f + (rnd.nextFloat() - 0.5f) * 0.9f; // ~[0.05,0.95], avg 0.5
			px[i] = (byte) (v * 255f);
			px[i + 1] = (byte) (v * 255f);
			px[i + 2] = (byte) (v * 255f);
			px[i + 3] = (byte) 255; // strength comes from the draw color's alpha
		}
		ByteBuffer buf = ByteBuffer.allocateDirect(px.length).order(ByteOrder.nativeOrder());
		buf.put(px).rewind();
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, tex);
		GLES10.glTexImage2D(GLES10.GL_TEXTURE_2D, 0, GLES10.GL_RGBA, NOISE_SIZE, NOISE_SIZE,
				0, GLES10.GL_RGBA, GLES10.GL_UNSIGNED_BYTE, buf);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, 0);
	}

	private static void destroy() {
		int[] tmp = new int[1];
		if (texGame != 0) { tmp[0] = texGame; GLES10.glDeleteTextures(1, tmp, 0); texGame = 0; }
		if (texA != 0) { tmp[0] = texA; GLES10.glDeleteTextures(1, tmp, 0); texA = 0; }
		if (texB != 0) { tmp[0] = texB; GLES10.glDeleteTextures(1, tmp, 0); texB = 0; }
		if (texT != 0) { tmp[0] = texT; GLES10.glDeleteTextures(1, tmp, 0); texT = 0; }
		if (maskTex != 0) { tmp[0] = maskTex; GLES10.glDeleteTextures(1, tmp, 0); maskTex = 0; }
		if (noiseTex1 != 0) { tmp[0] = noiseTex1; GLES10.glDeleteTextures(1, tmp, 0); noiseTex1 = 0; }
		if (noiseTex2 != 0) { tmp[0] = noiseTex2; GLES10.glDeleteTextures(1, tmp, 0); noiseTex2 = 0; }
		if (fboGame != 0) { tmp[0] = fboGame; GLES11Ext.glDeleteFramebuffersOES(1, tmp, 0); fboGame = 0; }
		if (fboA != 0) { tmp[0] = fboA; GLES11Ext.glDeleteFramebuffersOES(1, tmp, 0); fboA = 0; }
		if (fboB != 0) { tmp[0] = fboB; GLES11Ext.glDeleteFramebuffersOES(1, tmp, 0); fboB = 0; }
		if (fboT != 0) { tmp[0] = fboT; GLES11Ext.glDeleteFramebuffersOES(1, tmp, 0); fboT = 0; }
		if (rboDepth != 0) { tmp[0] = rboDepth; GLES11Ext.glDeleteRenderbuffersOES(1, tmp, 0); rboDepth = 0; }
		fw = fh = 0;
		potWGame = potHGame = potWA = potHA = potWB = potHB = potWT = potHT = 0;
		ready = false;
		maskPreset = -1;
	}

	// ------------------------------------------------------------------
	// Mask baking (CPU, once per preset change)
	// ------------------------------------------------------------------

	private static float smoothstep(float e0, float e1, float x) {
		float t = (x - e0) / (e1 - e0);
		if (t < 0f) t = 0f;
		else if (t > 1f) t = 1f;
		return t * t * (3f - 2f * t);
	}

	/**
	 * Bakes vignette * scanlines * warm/cool * brightness into a POT mask.
	 * warm > 0 tints orange (boost r, cut b); warm < 0 tints cool blue.
	 * Brightness folds in multiplicatively (the additive form has no exact
	 * fixed-function op; tiny factors are visually equivalent).
	 */
	private static void buildMaskTexture(float[] p) {
		float vignette = p[3];
		float scan = p[4];
		float warm = p[5];
		float bright = p[2];

		byte[] px = new byte[MASK_SIZE * MASK_SIZE * 4];
		for (int y = 0; y < MASK_SIZE; y++) {
			float fy = (y + 0.5f) / MASK_SIZE; // uv v, 0 at bottom
			boolean darkRow = scan > 0.001f && (y & 1) == 0;
			float scanFactor = darkRow ? (1f - scan * 0.6f) : 1f;
			for (int x = 0; x < MASK_SIZE; x++) {
				float fx = (x + 0.5f) / MASK_SIZE;
				float dx = fx - 0.5f;
				float dy = fy - 0.5f;
				float d = (float) Math.sqrt(dx * dx + dy * dy);
				float vg = vignette > 0.001f ? 1f - vignette * smoothstep(0.35f, 0.85f, d) : 1f;

				float m = vg * scanFactor * (1f + bright);
				float r = m * (1f + warm);
				float g = m;
				float b = m * (1f - warm * 0.8f);
				int idx = (y * MASK_SIZE + x) * 4;
				px[idx] = (byte) (clamp01(r) * 255f);
				px[idx + 1] = (byte) (clamp01(g) * 255f);
				px[idx + 2] = (byte) (clamp01(b) * 255f);
				px[idx + 3] = (byte) 255;
			}
		}

		ByteBuffer buf = ByteBuffer.allocateDirect(px.length).order(ByteOrder.nativeOrder());
		buf.put(px).rewind();

		GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, maskTex);
		GLES10.glTexImage2D(GLES10.GL_TEXTURE_2D, 0, GLES10.GL_RGBA, MASK_SIZE, MASK_SIZE, 0,
				GLES10.GL_RGBA, GLES10.GL_UNSIGNED_BYTE, buf);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, 0);
		Log.i(TAG, "mask baked for preset #" + preset());
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}