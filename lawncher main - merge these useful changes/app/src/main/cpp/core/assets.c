#include "hook.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <android/log.h>
#include <jni.h>
#include "core.h"

#define LOG_TAG "LawncherAssets"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

char g_internalfiles[256] = {0};
char g_externalfiles[256] = {0};

// ==========================================
// Current mod id
//
// Fetched from Java (MainActivity.currentMod() -> Launcher.currentMod())
// and cached after the first call. The mod can't change mid-session -
// Launch only ever fires from a single mod's detail screen - so we don't
// need to re-attach/call JNI on every single asset or save load.
// ==========================================

static JavaVM *g_vm = NULL;
static jclass g_mainActivityClass = NULL;
static jmethodID g_currentModMethod = NULL;

static char g_current_mod[128] = {0};
static pthread_once_t g_mod_once = PTHREAD_ONCE_INIT;

static void fetch_current_mod_once(void) {
	if (!g_vm || !g_mainActivityClass || !g_currentModMethod) {
		LOGE("current_mod_id(): JNI not ready yet, treating this session as vanilla");
		return;
	}

	JNIEnv *env = NULL;
	int attached = 0;
	int status = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
	if (status == JNI_EDETACHED) {
		if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
			LOGE("current_mod_id(): failed to attach thread to JVM");
			return;
		}
		attached = 1;
	} else if (status != JNI_OK) {
		LOGE("current_mod_id(): GetEnv failed (%d)", status);
		return;
	}

	jstring jmod = (jstring)(*env)->CallStaticObjectMethod(env, g_mainActivityClass, g_currentModMethod);
	if (jmod) {
		const char *chars = (*env)->GetStringUTFChars(env, jmod, NULL);
		if (chars) {
			snprintf(g_current_mod, sizeof(g_current_mod), "%s", chars);
			(*env)->ReleaseStringUTFChars(env, jmod, chars);
		}
		(*env)->DeleteLocalRef(env, jmod);
	}

	if (attached) {
		(*g_vm)->DetachCurrentThread(g_vm);
	}

	LOGI("current_mod_id(): playing with mod '%s'", g_current_mod[0] ? g_current_mod : "(none - vanilla)");
}

/** Mod id for this play session, or "" if none is selected. Cheap after the first call. */
static const char *current_mod_id(void) {
	pthread_once(&g_mod_once, fetch_current_mod_once);
	return g_current_mod;
}

// ==========================================
// Small path helpers
// ==========================================

static const char *path_basename(const char *path) {
	const char *slash = strrchr(path, '/');
	return slash ? slash + 1 : path;
}

/** mkdir -p, since Android's libc mkdir() only ever makes one level at a time. */
static void ensure_dir(const char *path) {
	char buf[512];
	snprintf(buf, sizeof(buf), "%s", path);

	for (char *p = buf + 1; *p; p++) {
		if (*p == '/') {
			*p = '\0';
			mkdir(buf, 0770);
			*p = '/';
		}
	}
	mkdir(buf, 0770);
}

/** Best-effort whole-file copy. Returns 1 on success, removing any partial output on failure. */
static int copy_file(const char *src, const char *dst) {
	FILE *in = fopen(src, "rb");
	if (in == NULL) return 0;

	FILE *out = fopen(dst, "wb");
	if (out == NULL) {
		fclose(in);
		return 0;
	}

	char buf[8192];
	size_t n;
	int ok = 1;
	while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
		if (fwrite(buf, 1, n, out) != n) {
			ok = 0;
			break;
		}
	}
	fclose(in);
	fclose(out);
	if (!ok) remove(dst);
	return ok;
}

/**
 * One-time seed: copies every file out of the shared vanilla Documents/
 * directory into a freshly-created mod saves/ directory.
 *
 * Why this exists: Caver::ProfileManager::GetAllProfiles() discovers save
 * slots by listing DocumentsDirectoryPath() directly on disk (a raw
 * directory scan) - it never goes through NewByteBufferFromFile, so our
 * read hook can't influence what it finds. That scan always sees the real
 * shared Documents/ folder, no matter which mod is active. It then calls
 * PlayerProfile::Load() for each file it found there, which DOES go
 * through our (isolated) read hook - and under full isolation that comes
 * back NULL for a mod that has never saved yet, for every slot the engine
 * just told itself exists. That mismatch is what produced the "3 weird
 * empty-looking slots" and the crash right after loading them.
 *
 * Seeding once, right when the mod's saves/ dir is first created, keeps
 * the (unhooked) directory listing and the (hooked) per-file reads in
 * agreement - same filenames the engine already knows about, now with
 * real content behind them for this mod's very first session. Isolation
 * is unaffected going forward: only these copies are ever read or written
 * for this mod from here on, never the vanilla originals.
 */
static void seed_mod_saves_from_vanilla(const char *savesDir) {
	if (g_internalfiles[0] == '\0') return;

	char vanillaDocs[300];
	snprintf(vanillaDocs, sizeof(vanillaDocs), "%s/Documents", g_internalfiles);

	DIR *dir = opendir(vanillaDocs);
	if (dir == NULL) {
		LOGI("seed_mod_saves_from_vanilla(): no vanilla Documents dir at %s yet (fresh install) - nothing to seed", vanillaDocs);
		return;
	}

	int copied = 0;
	struct dirent *entry;
	while ((entry = readdir(dir)) != NULL) {
		if (entry->d_name[0] == '.') continue; // skip "." / ".." / dotfiles

		char src[512], dst[512];
		snprintf(src, sizeof(src), "%s/%s", vanillaDocs, entry->d_name);
		snprintf(dst, sizeof(dst), "%s/%s", savesDir, entry->d_name);

		struct stat st;
		if (stat(src, &st) != 0 || !S_ISREG(st.st_mode)) continue; // only plain save files

		if (copy_file(src, dst)) {
			copied++;
			LOGI("seed_mod_saves_from_vanilla(): seeded %s", entry->d_name);
		} else {
			LOGE("seed_mod_saves_from_vanilla(): failed to seed %s", entry->d_name);
		}
	}
	closedir(dir);

	LOGI("seed_mod_saves_from_vanilla(): seeded %d file(s) into %s", copied, savesDir);
}

static pthread_mutex_t g_seed_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_seed_done = 0;

/**
 * .../mods/<id>/saves, created (and seeded from vanilla - see
 * seed_mod_saves_from_vanilla() above) the first time it's needed this
 * session. Returns 0 if no mod is active (or external path isn't ready),
 * meaning the caller should treat this as a plain vanilla session.
 *
 * Saves are per-mod and fully isolated from here on out: unlike assets,
 * once seeded they never fall back to (or write to) the shared vanilla
 * save on disk. See the read/write hooks below.
 */
static int mod_saves_dir(char *out, size_t out_size) {
	const char *mod = current_mod_id();
	if (!mod[0] || g_externalfiles[0] == '\0') return 0;

	snprintf(out, out_size, "%s/mods/%s/saves", g_externalfiles, mod);
	ensure_dir(out);

	// One-shot per session: the mod can't change mid-session (see the
	// comment on current_mod_id() above). Hold the mutex for the whole
	// seed operation (not just the flag flip) so a second thread calling
	// this concurrently - e.g. an asset load and a save load racing at
	// startup, as seen in the logs - blocks until seeding has actually
	// finished, instead of reading/writing a half-seeded directory.
	pthread_mutex_lock(&g_seed_mutex);
	if (!g_seed_done) {
		seed_mod_saves_from_vanilla(out);
		g_seed_done = 1;
	}
	pthread_mutex_unlock(&g_seed_mutex);

	return 1;
}

// ==========================================
// Hooks
// ==========================================

HOOK_SYMBOL(
	ByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKSsPj",
	void*, (char **path, int *n)
) {
	const char *file_path = *path;
	LOGD("%s", file_path);

	// Save files (.gplayer / .gopt / etc.) normally live under
	// files/Documents/<uuid>.ext, shared by every mod and vanilla alike.
	// If a mod is active, saves are fully isolated to that mod's own
	// saves/ folder - both directions. A miss here must NOT fall back to
	// the shared vanilla save (orig_ByteBufferFromFile below), since that
	// would leak or later let us clobber vanilla's/another mod's progress.
	char saveDir[512];
	if (file_path && strstr(file_path, "/Documents/") && mod_saves_dir(saveDir, sizeof(saveDir))) {
		char redirected[600];
		snprintf(redirected, sizeof(redirected), "%s/%s", saveDir, path_basename(file_path));

		FILE *f = fopen(redirected, "rb");
		if (f != NULL) {
			fseek(f, 0, SEEK_END);
			long size = ftell(f);
			fseek(f, 0, SEEK_SET);

			if (size > 0) {
				void *buffer = malloc(size);
				if (buffer != NULL) {
					size_t read_bytes = fread(buffer, 1, size, f);
					fclose(f);
					if (read_bytes == size) {
						LOGI("SUCCESS: Loaded mod-specific save %s", redirected);
						if (n != NULL) *n = (int)size;
						return buffer;
					}
					LOGE("ERROR: Failed to read all bytes from %s", redirected);
					free(buffer);
				} else {
					LOGE("ERROR: Failed to allocate %ld bytes for %s", size, redirected);
					fclose(f);
				}
			} else {
				fclose(f);
			}
		}

		// No mod-specific save yet (e.g. first run) or a read error above -
		// report "not found" the same way vanilla's own fopen-fails path
		// does (NULL, *n left untouched), so the game treats it as a fresh
		// profile scoped to this mod. Do NOT fall through to
		// orig_ByteBufferFromFile - that would read the shared vanilla path.
		LOGI("No mod-specific save at %s - reporting missing (mod-isolated, no vanilla fallback)", redirected);
		return NULL;
	}

	// Not a /Documents/ (save-type) path, or no mod active - plain vanilla
	// file read, unchanged.
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
	char saveDir[512];
	if (file_path && strstr(file_path, "/Documents/") && mod_saves_dir(saveDir, sizeof(saveDir))) {
		char redirected[600];
		snprintf(redirected, sizeof(redirected), "%s/%s", saveDir, path_basename(file_path));

		// Write-then-rename so a crash or full disk mid-write can't leave a
		// truncated save behind - mirrors the temp-file dance the vanilla
		// implementation already does with its own save path.
		char tmp[620];
		snprintf(tmp, sizeof(tmp), "%s.tmp", redirected);

		FILE *f = fopen(tmp, "wb");
		if (f != NULL) {
			size_t written = fwrite(data, 1, size, f);
			fclose(f);
			if (written == size) {
				if (rename(tmp, redirected) == 0) {
					LOGI("SUCCESS: Saved mod-specific save %s (%u bytes)", redirected, size);
					return 1;
				}
				LOGE("ERROR: Failed to rename %s -> %s", tmp, redirected);
				remove(tmp);
			} else {
				LOGE("ERROR: Short write to %s (%zu/%u bytes)", tmp, written, size);
				remove(tmp);
			}
		} else {
			LOGE("ERROR: Failed to open %s for writing", tmp);
		}
		// Any failure above falls through to the vanilla save call below so
		// we never silently drop the player's progress. This only happens
		// on genuine I/O errors, not on "no mod active" (already excluded
		// by the mod_saves_dir() check above).
	}

	return orig_SaveByteBufferToFile(data, size, path);
}

HOOK_SYMBOL(
	NewByteBuffer,
	"_ZN5Caver29NewByteBufferFromAndroidAssetERKSsPj",
	void*, (char **path, int *n)
) {
	const char *asset_path = *path;
	const char *mod = current_mod_id();

	// Only proceed if we have a valid path, a mod is actually selected, and
	// the external path is initialized.
	if (asset_path && mod[0] != '\0' && g_externalfiles[0] != '\0') {
		// asset_path is typically "resources/filename.ext", so this maps to
		// .../files/mods/<modId>/resources/filename.ext - matching the mod's
		// on-disk layout exactly (see ModManager.java on the launcher side).
		char full_path[512];
		snprintf(full_path, sizeof(full_path), "%s/mods/%s/%s", g_externalfiles, mod, asset_path);

		FILE *f = fopen(full_path, "rb");
		if (f != NULL) {
			fseek(f, 0, SEEK_END);
			long size = ftell(f);
			fseek(f, 0, SEEK_SET);
			if (size > 0) {
				void *buffer = malloc(size);
				if (buffer != NULL) {
					size_t read_bytes = fread(buffer, 1, size, f);
					fclose(f);
					if (read_bytes == size) {
						LOGI("SUCCESS: Loaded override for %s from mod '%s'!", asset_path, mod);
						if (n != NULL) {
							*n = (int)size;
						}
						return buffer; // Return our custom buffer instead of the vanilla asset
					} else {
						LOGE("ERROR: Failed to read all bytes from %s", full_path);
						free(buffer);
					}
				} else {
					LOGE("ERROR: Failed to allocate %ld bytes for %s", size, full_path);
					fclose(f);
				}
			} else {
				LOGE("ERROR: File %s is empty or invalid.", full_path);
				fclose(f);
			}
		}
		// If fopen fails (f == NULL), the mod simply doesn't override this
		// specific file - silently drop down to the vanilla fallback.
	}

	// Fallback for every file that isn't overridden by the current mod
	// (or when no mod is selected at all - plain vanilla). Assets are
	// read-only shared content, so - unlike saves - falling back here is
	// intentional and correct.
	return orig_NewByteBuffer(path, n);
}

void init_assets(void)
{
	LOGI("init_assets() asset override active");
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_initPaths(JNIEnv *env, jclass clazz, jstring internalFiles, jstring externalFiles) {
	if (internalFiles) {
		const char *int_path = (*env)->GetStringUTFChars(env, internalFiles, NULL);
		snprintf(g_internalfiles, sizeof(g_internalfiles), "%s", int_path);
		(*env)->ReleaseStringUTFChars(env, internalFiles, int_path);
	}
	if (externalFiles) {
		const char *ext_path = (*env)->GetStringUTFChars(env, externalFiles, NULL);
		snprintf(g_externalfiles, sizeof(g_externalfiles), "%s", ext_path);
		(*env)->ReleaseStringUTFChars(env, externalFiles, ext_path);
	}

	// Cache what we need to call back into MainActivity.currentMod() later,
	// from whatever native thread the game ends up loading assets/saves on.
	(*env)->GetJavaVM(env, &g_vm);
	g_mainActivityClass = (jclass)(*env)->NewGlobalRef(env, clazz);
	g_currentModMethod = (*env)->GetStaticMethodID(env, g_mainActivityClass, "currentMod", "()Ljava/lang/String;");
	if (!g_currentModMethod) {
		LOGE("Couldn't find MainActivity.currentMod() - did you add the passthrough method to Launcher.currentMod()?");
		(*env)->ExceptionClear(env);
	}

	LOGI("Paths captured -> Internal: %s | External: %s", g_internalfiles, g_externalfiles);
}