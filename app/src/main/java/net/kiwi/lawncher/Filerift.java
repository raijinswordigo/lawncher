package net.kiwi.lawncher.filerift;

import android.graphics.Bitmap;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Java facade for the native FileRift engine + POD parser embedded in the
 * launcher (app/src/main/cpp/lawncher/).
 *
 * Raijin's request: "pod viewer + filerift native support" — when the user
 * clicks a .scl / .scene / .gplayer (etc.) file it is decoded to readable
 * FileRift markup and opened in the embedded Xed editor; on save it is
 * re-encoded to binary under the hood.
 *
 * All methods are synchronous native calls; keep them off the UI thread for
 * anything non-trivial (decode/recode of big scenes).
 */
@Keep
public final class Filerift {

	private Filerift() {}

	/** Decode binary protobuf bytes to readable FileRift markup. */
	public static native String decode(byte[] data, String filetype);

	/** Re-encode FileRift markup back to binary protobuf bytes. */
	public static native byte[] recode(String text, String filetype);

	/** Extract embedded Lua source from .scl/.scene binary data. */
	public static native String extractLua(byte[] data);

	/** POD model metadata summary (viewer backend). */
	public static native String podSummary(byte[] data);

	/**
	 * Decode a .pvr / .tex texture to RGBA. Returns byte[]
	 * {w_lo, w_hi, h_lo, h_hi, r,g,b,a,...} or null on failure.
	 * Backend for the PVR / texpng viewer.
	 */
	public static native byte[] decodeTexture(byte[] data);

	/**
	 * Render a POD model with the software renderer. Returns byte[]
	 * {w_lo, w_hi, h_lo, h_hi, r,g,b,a,...} or null on failure.
	 */
	public static native byte[] renderPod(byte[] data, int width, int height,
	                                      float rotY, float rotX, float zoom,
	                                      boolean wireframe);

	/** One-line human-readable POD summary for viewer titles. */
	public static native String podInfo(byte[] data);

	// ─── OpenGL POD viewer (GLSurfaceView backend) ──────────────────────
	// These must be called from the GL thread (current EGL context).

	/** Compile the POD viewer shaders. Call once on the GL thread. */
	public static native boolean podGLInit();

	/** Load a POD into GPU memory; returns a handle (or -1). GL thread. */
	public static native int podGLLoad(byte[] data, String baseDir);

	/** Render one frame of the model into the current framebuffer. GL thread. */
	public static native void podGLRender(int handle, int width, int height,
	                                      float yaw, float pitch, float dist,
	                                      float frame, boolean wireframe,
	                                      boolean showGrid, boolean autoRotate);

	/** Release a loaded model's GPU resources. GL thread. */
	public static native void podGLFree(int handle);

	/** One-line info for a loaded model. */
	public static native String podGLInfo(int handle);

	/** Animation frame count for a loaded model (0 = static). */
	public static native int podGLFrameCount(int handle);

	/** Decode a .pvr / .tex file to an Android Bitmap, or null on failure. */
	public static Bitmap decodeTextureBitmap(File file) {
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			return decodeTextureBitmap(bytes);
		} catch (IOException e) {
			return null;
		}
	}

	/** Decode a .pvr / .tex buffer to an Android Bitmap, or null on failure. */
	public static Bitmap decodeTextureBitmap(byte[] data) {
		byte[] packed = decodeTexture(data);
		if (packed == null || packed.length < 6) return null;
		int w = (packed[0] & 0xFF) | ((packed[1] & 0xFF) << 8);
		int h = (packed[2] & 0xFF) | ((packed[3] & 0xFF) << 8);
		if (w <= 0 || h <= 0 || (long) w * h * 4 + 4 != packed.length) return null;

		int[] pixels = new int[w * h];
		for (int i = 0; i < w * h; ++i) {
			int r = packed[4 + i * 4] & 0xFF;
			int g = packed[4 + i * 4 + 1] & 0xFF;
			int b = packed[4 + i * 4 + 2] & 0xFF;
			int a = packed[4 + i * 4 + 3] & 0xFF;
			pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
	}

	/** Render a POD model to an Android Bitmap, or null on failure. */
	public static Bitmap renderPodBitmap(byte[] data, int width, int height,
	                                     float rotY, float rotX, float zoom,
	                                     boolean wireframe) {
		byte[] packed = renderPod(data, width, height, rotY, rotX, zoom, wireframe);
		if (packed == null || packed.length < 6) return null;
		int w = (packed[0] & 0xFF) | ((packed[1] & 0xFF) << 8);
		int h = (packed[2] & 0xFF) | ((packed[3] & 0xFF) << 8);
		if (w <= 0 || h <= 0 || (long) w * h * 4 + 4 != packed.length) return null;

		int[] pixels = new int[w * h];
		for (int i = 0; i < w * h; ++i) {
			int r = packed[4 + i * 4] & 0xFF;
			int g = packed[4 + i * 4 + 1] & 0xFF;
			int b = packed[4 + i * 4 + 2] & 0xFF;
			pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
		}
		return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
	}

	// ─── Convenience ─────────────────────────────────────────────────────

	/** Filetype key used by the native engine, from a filename. */
	public static String filetypeFor(String filename) {
		if (filename == null) return "";
		String lower = filename.toLowerCase(Locale.ROOT);
		for (String ext : new String[]{
		".scene", ".scl", ".gdata", ".gstate", ".gopt", ".gplayer",
		".remap", ".sounds", ".scmap", ".fr", ".gmesh", ".gvar", ".bootup"}) {
			if (lower.endsWith(ext)) {
				return ext.substring(1);
			}
		}
		return "";
	}

	/** True when this filename is a Swordigo binary file Filerift can decode. */
	public static boolean isDecodable(String filename) {
		return !filetypeFor(filename).isEmpty();
	}

	/** Decode a file from disk to markup text. Returns null on failure. */
	public static String decodeFile(File file) {
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			String type = filetypeFor(file.getName());
			if (type.isEmpty()) return null;
			return decode(bytes, type);
		} catch (IOException e) {
			return null;
		}
	}

	/** Re-encode markup text back to binary and write over the file. */
	public static boolean recodeFile(File file, String markup) {
		try {
			String type = filetypeFor(file.getName());
			if (type.isEmpty()) return false;
			byte[] binary = recode(markup, type);
			if (binary == null || binary.length == 0) return false;
			Files.write(file.toPath(), binary);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
