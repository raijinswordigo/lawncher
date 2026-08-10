#include "hook.h"
#include "vfs.h"
#include <string.h>
#include <android/log.h>

#define LOG_TAG "LawncherAssets"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * These three hooks are the entire native mod boundary. All filesystem
 * intelligence lives in core/vfs.c (layered asset overrides, isolated
 * per-mod saves, path containment, seeding, negative cache) — the hooks
 * here just translate game calls into VFS calls and decide the fallback
 * policy for each scope:
 *
 *   assets  ->  mod overlay, then vanilla fallback (read-only shared data)
 *   saves   ->  mod-isolated only, NO vanilla fallback (progress privacy)
 */

HOOK_SYMBOL(
	ByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKSsPj",
	void*, (char **path, int *n)
) {
	const char *file_path = *path;

	// Save files (.gplayer / .gopt / etc.) live under files/Documents/.
	// While a mod is active they are fully isolated to that mod's saves/
	// folder — both directions. A miss here MUST NOT fall back to the
	// shared vanilla save (orig_ByteBufferFromFile below): that would leak
	// (or later let us clobber) vanilla's / another mod's progress.
	if (file_path && strstr(file_path, "/Documents/") && vfs_mod_active()) {
		int size = 0;
		void *buffer = vfs_read_save(file_path, &size);
		if (buffer) {
			LOGI("SUCCESS: Loaded mod-isolated save %s (%d bytes)", file_path, size);
			if (n != NULL) *n = size;
			return buffer;
		}

		// No mod-specific save yet (first run, or a fresh profile). Report
		// "not found" exactly the way vanilla's own fopen-fails path does
		// (NULL, *n untouched) so the game treats it as a fresh profile
		// scoped to this mod. No vanilla fallback — isolation is the point.
		LOGI("No mod-specific save at %s — reporting missing (mod-isolated, no vanilla fallback)", file_path);
		return NULL;
	}

	// Not a save-type path, or no mod active — plain vanilla file read.
	return orig_ByteBufferFromFile(path, n);
}

HOOK_SYMBOL(
	SaveByteBufferToFile,
	"_ZN5Caver20SaveByteBufferToFileEPKhjRKSs",
	int, (void *data, unsigned int size, char **path)
) {
	const char *file_path = *path;

	// Mirror of the read hook: while a mod is active, save writes are
	// redirected into that mod's own saves/ folder only, and never touch
	// the shared vanilla save file.
	if (file_path && strstr(file_path, "/Documents/") && vfs_mod_active()) {
		if (vfs_write_save(file_path, data, size)) {
			LOGI("SUCCESS: Saved mod-isolated save %s (%u bytes)", file_path, size);
			return 1;
		}
		// Genuine I/O failure (full disk, permissions, ...) — fall through
		// to the vanilla write below so we never silently drop progress.
		// This only happens on real errors, not on "no mod active"
		// (already excluded by vfs_mod_active() above).
		LOGE("ERROR: Mod save write failed for %s — falling back to vanilla path", file_path);
	}

	return orig_SaveByteBufferToFile(data, size, path);
}

HOOK_SYMBOL(
	NewByteBuffer,
	"_ZN5Caver29NewByteBufferFromAndroidAssetERKSsPj",
	void*, (char **path, int *n)
) {
	const char *asset_path = *path;

	if (asset_path) {
		int size = 0;
		void *buffer = vfs_read_asset(asset_path, &size);
		if (buffer) {
			LOGI("SUCCESS: Loaded override for %s from mod '%s'!", asset_path, vfs_mod_id());
			if (n != NULL) *n = size;
			return buffer;
		}
		// No override for this file (or no mod active) — the VFS's layered
		// lookup already decided; drop down to the vanilla asset. Assets
		// are read-only shared content, so — unlike saves — falling back
		// here is intentional and correct.
	}

	return orig_NewByteBuffer(path, n);
}

void init_assets(void)
{
	vfs_init();
	LOGI("init_assets(): mod VFS ready (asset override layer active)");
}
