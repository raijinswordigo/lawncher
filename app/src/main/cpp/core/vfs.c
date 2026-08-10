#include "vfs.h"
#include "log.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <jni.h>

#define LOG_TAG "LawncherVFS"

// ==========================================
// Session state
//
// Populated two ways:
//   1. setSession() from Java (MainActivity) — the exact, authoritative
//      values, used by every real launch.
//   2. A lazy JNI fallback (fetch_session_once) for native-only sessions
//      where Java never called setSession.
// Whichever happens first wins; the lazy path backs off if setSession ran.
// ==========================================

static JavaVM *g_vm = NULL;
static jclass g_mainActivityClass = NULL;
static jmethodID g_currentModMethod = NULL;
static jmethodID g_currentInstanceMethod = NULL;

static char g_internal_root[256] = {0};
static char g_external_root[256] = {0};
static char g_instance_id[128] = {0};
static char g_mod_id[128] = {0};
static int g_session_set = 0;
static pthread_once_t g_fetch_once = PTHREAD_ONCE_INIT;

// Negative asset cache (see below) — declared up here because
// vfs_set_session() clears it on session change.
#define VFS_CACHE_SLOTS 512
#define VFS_CACHE_KEY_MAX 384

static char g_miss_cache[VFS_CACHE_SLOTS][VFS_CACHE_KEY_MAX];
static unsigned char g_miss_used[VFS_CACHE_SLOTS];
static pthread_mutex_t g_cache_mutex = PTHREAD_MUTEX_INITIALIZER;

static void fetch_session_once(void) {
	if (g_session_set) return; // setSession() already told us — trust it.
	if (!g_vm || !g_mainActivityClass || !g_currentModMethod || !g_currentInstanceMethod) {
		LOGW("vfs: JNI not ready yet, treating this session as vanilla");
		return;
	}

	JNIEnv *env = NULL;
	int attached = 0;
	int status = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
	if (status == JNI_EDETACHED) {
		if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
			LOGE("vfs: failed to attach thread to JVM");
			return;
		}
		attached = 1;
	} else if (status != JNI_OK) {
		LOGE("vfs: GetEnv failed (%d)", status);
		return;
	}

	jstring jmod = (jstring)(*env)->CallStaticObjectMethod(env, g_mainActivityClass, g_currentModMethod);
	if (jmod) {
		const char *chars = (*env)->GetStringUTFChars(env, jmod, NULL);
		if (chars) {
			snprintf(g_mod_id, sizeof(g_mod_id), "%s", chars);
			(*env)->ReleaseStringUTFChars(env, jmod, chars);
		}
		(*env)->DeleteLocalRef(env, jmod);
	}

	jstring jinst = (jstring)(*env)->CallStaticObjectMethod(env, g_mainActivityClass, g_currentInstanceMethod);
	if (jinst) {
		const char *chars = (*env)->GetStringUTFChars(env, jinst, NULL);
		if (chars) {
			snprintf(g_instance_id, sizeof(g_instance_id), "%s", chars);
			(*env)->ReleaseStringUTFChars(env, jinst, chars);
		}
		(*env)->DeleteLocalRef(env, jinst);
	}

	if (attached) {
		(*g_vm)->DetachCurrentThread(g_vm);
	}

	LOGI("vfs: playing with instance '%s' mod '%s'",
	     g_instance_id[0] ? g_instance_id : "(none)",
	     g_mod_id[0] ? g_mod_id : "(none - vanilla)");
}

void vfs_set_paths(const char *internal_root, const char *external_root) {
	if (internal_root) {
		snprintf(g_internal_root, sizeof(g_internal_root), "%s", internal_root);
	}
	if (external_root) {
		snprintf(g_external_root, sizeof(g_external_root), "%s", external_root);
	}
}

void vfs_set_session(const char *instance_id, const char *mod_id) {
	const char *inst = instance_id ? instance_id : "";
	const char *mod = mod_id ? mod_id : "";

	int changed = strcmp(g_instance_id, inst) != 0 || strcmp(g_mod_id, mod) != 0;
	snprintf(g_instance_id, sizeof(g_instance_id), "%s", inst);
	snprintf(g_mod_id, sizeof(g_mod_id), "%s", mod);
	g_session_set = 1;

	// The negative asset cache is valid only while the session's mod is
	// immutable (true for a single session). If a new (instance, mod) pair
	// is launched in this process, mod A's misses must not be inherited by
	// mod B — clear the cache so every path is re-checked against the new
	// mod's tree.
	if (changed) {
		pthread_mutex_lock(&g_cache_mutex);
		memset(g_miss_used, 0, sizeof(g_miss_used));
		pthread_mutex_unlock(&g_cache_mutex);
		LOGI("vfs: session changed -> asset miss cache cleared");
	}

	LOGI("vfs: setSession() -> instance='%s' mod='%s'", g_instance_id, g_mod_id);
}

const char *vfs_mod_id(void) {
	pthread_once(&g_fetch_once, fetch_session_once);
	return g_mod_id;
}

const char *vfs_instance_id(void) {
	pthread_once(&g_fetch_once, fetch_session_once);
	return g_instance_id;
}

int vfs_mod_active(void) {
	return g_external_root[0] != '\0' && vfs_mod_id()[0] != '\0';
}

// ==========================================
// Mount table
//
// The active mod's on-disk root:
//   <external>/instances/<instanceId>/mods/<modId>   (per-instance mods)
//   <external>/mods/<modId>                          (legacy global mods)
// Save files live under <root>/saves; assets are requested by the game as
// paths like "resources/foo.png" and resolved as <root>/<game_path>.
// ==========================================

/**
 * Sanitizes an id the same way Java's ModManager.sanitizeName does:
 * trim surrounding whitespace, then replace every character outside
 * [A-Za-z0-9._-] with '_'. The Java installer names the mod's on-disk
 * directory with this transform, so the native mount path MUST use the
 * identically-transformed id or mods whose ids contain spaces/symbols
 * (e.g. "My Mod: v2") would silently never load. The raw id (what
 * `lawncher.mod` and the Lua `mod_name` report) is untouched.
 */
static void dirify_id(char *out, size_t n, const char *raw) {
	size_t o = 0;
	const char *p = raw;
	while (*p == ' ') p++; // leading trim
	const char *end = p + strlen(p);
	while (end > p && end[-1] == ' ') end--; // trailing trim

	for (; p < end && o + 1 < n; p++) {
		unsigned char c = (unsigned char)*p;
		int keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
		           (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
		out[o++] = keep ? (char)c : '_';
	}
	out[o] = '\0';
}

/** Writes the active mod's root into out. Returns 0 when no mod is active. */
static int mod_root(char *out, size_t n) {
	if (g_external_root[0] == '\0') return 0;
	const char *mod = vfs_mod_id();
	if (mod[0] == '\0') return 0;

	char dir[128];
	dirify_id(dir, sizeof(dir), mod);

	if (dir[0] == '\0') return 0;

	if (g_instance_id[0]) {
		snprintf(out, n, "%s/instances/%s/mods/%s", g_external_root, g_instance_id, dir);
	} else {
		snprintf(out, n, "%s/mods/%s", g_external_root, dir);
	}
	return 1;
}

// ==========================================
// Path normalization + containment
//
// Canonicalizes a mod-relative path ("a/./b/../c" -> "a/c") and REFUSES
// any path whose ".." segments would climb above the mod's own tree.
// Returns 1 and fills out when contained; 0 otherwise (absolute paths and
// escapes are rejected — the caller treats them as vanilla / invalid).
// ==========================================

static int vfs_normalize_rel(const char *in, char *out, size_t n) {
	if (!in || in[0] == '\0' || in[0] == '/') return 0; // empty or absolute

	size_t o = 0;
	const char *p = in;
	while (*p) {
		const char *seg = p;
		while (*p && *p != '/') p++;
		size_t len = (size_t)(p - seg);
		if (*p == '/') p++; // consume separator

		if (len == 0 || (len == 1 && seg[0] == '.')) continue;
		if (len == 2 && seg[0] == '.' && seg[1] == '.') {
			// climb one level; escaping the mod root is refused outright.
			// Check BEFORE popping: popping the first segment is legal
			// ("a/../c" -> "c"), popping nothing is an escape.
			if (o == 0) return 0; // escape attempt at the root
			while (o > 0 && out[o - 1] != '/') o--; // pop previous segment
			if (o > 0) o--; // drop the trailing '/'
			continue;
		}

		if (o + len + 2 > n) return 0; // would overflow -> reject
		if (o > 0) out[o++] = '/';
		memcpy(out + o, seg, len);
		o += len;
	}
	if (o == 0) return 0; // normalized to nothing
	out[o] = '\0';
	return 1;
}

// ==========================================
// Bounded negative cache (asset misses only)
//
// Mod files are immutable while the game runs (installs only happen from
// the launcher, before launch), so a file that doesn't exist now will
// never exist this session. Every missed asset load is remembered, and
// repeated per-frame requests for non-overridden files skip fopen entirely.
// Direct-mapped, 512 slots, keyed by the SHORT normalized game path.
// ==========================================

static unsigned cache_slot(const char *p) {
	unsigned h = 2166136261u;
	while (*p) {
		h ^= (unsigned char)*p++;
		h *= 16777619u;
	}
	return h % VFS_CACHE_SLOTS;
}

static int cache_is_missing(const char *key) {
	pthread_mutex_lock(&g_cache_mutex);
	int hit = g_miss_used[cache_slot(key)] && strcmp(g_miss_cache[cache_slot(key)], key) == 0;
	pthread_mutex_unlock(&g_cache_mutex);
	return hit;
}

static void cache_mark_missing(const char *key) {
	if (strlen(key) >= VFS_CACHE_KEY_MAX) return;
	pthread_mutex_lock(&g_cache_mutex);
	unsigned s = cache_slot(key);
	snprintf(g_miss_cache[s], VFS_CACHE_KEY_MAX, "%s", key);
	g_miss_used[s] = 1;
	pthread_mutex_unlock(&g_cache_mutex);
}

void vfs_init(void) {
	memset(g_miss_used, 0, sizeof(g_miss_used));
	LOGI("vfs: initialized (mod VFS active)");
}

// ==========================================
// Shared file reader
// ==========================================

#define VFS_MAX_FILE_SIZE (512u << 20) // defensive 512 MB cap

/** Reads a whole file into a malloc'd buffer. NULL on any failure/empty. */
static void *vfs_read_file_at(const char *full_path, int *out_size) {
	FILE *f = fopen(full_path, "rb");
	if (f == NULL) return NULL;

	fseek(f, 0, SEEK_END);
	long size = ftell(f);
	fseek(f, 0, SEEK_SET);

	if (size <= 0 || (unsigned long)size > VFS_MAX_FILE_SIZE) {
		fclose(f);
		return NULL; // empty or absurd -> treated as missing
	}

	void *buffer = malloc((size_t)size);
	if (buffer == NULL) {
		fclose(f);
		return NULL;
	}
	size_t got = fread(buffer, 1, (size_t)size, f);
	fclose(f);
	if (got != (size_t)size) {
		free(buffer);
		return NULL;
	}
	if (out_size) *out_size = (int)size;
	return buffer;
}

// ==========================================
// Asset layer
// ==========================================

void *vfs_read_asset(const char *game_path, int *out_size) {
	if (!game_path || !vfs_mod_active()) return NULL;

	char rel[384];
	if (!vfs_normalize_rel(game_path, rel, sizeof(rel))) return NULL; // not contained

	if (cache_is_missing(rel)) return NULL;

	char root[640], full[1024];
	if (!mod_root(root, sizeof(root))) return NULL;
	int r = snprintf(full, sizeof(full), "%s/%s", root, rel);
	if (r < 0 || (size_t)r >= sizeof(full)) return NULL;

	void *buffer = vfs_read_file_at(full, out_size);
	if (buffer == NULL) cache_mark_missing(rel);
	return buffer; // NULL == not overridden -> caller falls back to vanilla
}

int vfs_asset_exists(const char *game_path) {
	if (!game_path || !vfs_mod_active()) return 0;

	char rel[384];
	if (!vfs_normalize_rel(game_path, rel, sizeof(rel))) return 0;
	if (cache_is_missing(rel)) return 0;

	char root[640], full[1024];
	if (!mod_root(root, sizeof(root))) return 0;
	int r = snprintf(full, sizeof(full), "%s/%s", root, rel);
	if (r < 0 || (size_t)r >= sizeof(full)) return 0;

	struct stat st;
	if (stat(full, &st) != 0 || !S_ISREG(st.st_mode)) {
		cache_mark_missing(rel);
		return 0;
	}
	return 1;
}

// ==========================================
// Save layer (fully mod-isolated)
// ==========================================

static pthread_mutex_t g_seed_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_seeded_instance[128] = {0};
static char g_seeded_mod[128] = {0};

/** mkdir -p (Android libc mkdir only makes one level at a time). */
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

/** Best-effort whole-file copy. Returns 1 on success, removing partial output on failure. */
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
 * One-time seed per (instance, mod) session: copies every plain file out of
 * the shared vanilla Documents/ dir into this mod's freshly created saves/.
 *
 * Why: Caver::ProfileManager::GetAllProfiles() discovers save slots by
 * listing DocumentsDirectoryPath() directly on disk (a raw directory scan)
 * — it never goes through NewByteBufferFromFile, so our read hook can't
 * influence what it finds. That scan always sees the real shared Documents/
 * folder. It then calls PlayerProfile::Load() for each file it found, which
 * DOES go through our (isolated) read hook — and under full isolation that
 * comes back NULL for a mod that has never saved yet, for every slot the
 * engine just told itself exists. That mismatch produced the "3 weird
 * empty-looking slots" bug.
 *
 * Seeding right when the mod's saves/ dir is first created keeps the
 * (unhooked) directory listing and the (hooked) per-file reads in
 * agreement. Isolation is unaffected going forward: only these copies are
 * ever read or written for this mod from here on.
 *
 * The seed is keyed to the CURRENT (instance, mod) pair — NOT a
 * process-global one-shot — so if a second mod/instance is launched in the
 * same process (setSession called again), its saves get seeded too.
 */
static void seed_saves_from_vanilla(const char *saves_dir) {
	if (g_internal_root[0] == '\0') return;

	char vanilla_docs[300];
	snprintf(vanilla_docs, sizeof(vanilla_docs), "%s/Documents", g_internal_root);

	DIR *dir = opendir(vanilla_docs);
	if (dir == NULL) {
		LOGI("vfs: no vanilla Documents dir at %s yet (fresh install) — nothing to seed", vanilla_docs);
		return;
	}

	int copied = 0;
	struct dirent *entry;
	while ((entry = readdir(dir)) != NULL) {
		if (entry->d_name[0] == '.') continue; // "." / ".." / dotfiles

		char src[512], dst[512];
		snprintf(src, sizeof(src), "%s/%s", vanilla_docs, entry->d_name);
		snprintf(dst, sizeof(dst), "%s/%s", saves_dir, entry->d_name);

		struct stat st;
		if (stat(src, &st) != 0 || !S_ISREG(st.st_mode)) continue; // only plain save files

		if (copy_file(src, dst)) {
			copied++;
			LOGI("vfs: seeded %s", entry->d_name);
		} else {
			LOGE("vfs: failed to seed %s", entry->d_name);
		}
	}
	closedir(dir);

	LOGI("vfs: seeded %d file(s) into %s", copied, saves_dir);
}

/**
 * Resolves (and creates) the active mod's saves/ dir, seeding from vanilla
 * once per session. Returns 0 when no mod is active.
 */
static int saves_dir(char *out, size_t n) {
	if (!mod_root(out, n)) return 0;

	size_t base = strlen(out);
	if (base + 7 > n) return 0;
	snprintf(out + base, n - base, "/saves");
	ensure_dir(out);

	pthread_mutex_lock(&g_seed_mutex);
	if (strcmp(g_seeded_instance, g_instance_id) != 0 ||
	    strcmp(g_seeded_mod, g_mod_id) != 0) {
		seed_saves_from_vanilla(out);
		snprintf(g_seeded_instance, sizeof(g_seeded_instance), "%s", g_instance_id);
		snprintf(g_seeded_mod, sizeof(g_seeded_mod), "%s", g_mod_id);
	}
	pthread_mutex_unlock(&g_seed_mutex);

	return 1;
}

/** Safe basename of a game save path ("files/Documents/<uuid>.gplayer"). */
static int save_basename(const char *game_path, char *out, size_t n) {
	const char *slash = strrchr(game_path, '/');
	const char *base = slash ? slash + 1 : game_path;
	if (base[0] == '\0') return 0;
	if (strcmp(base, ".") == 0 || strcmp(base, "..") == 0) return 0;
	if (strchr(base, '/') != NULL) return 0; // defensive; can't happen post-strrchr
	if (strlen(base) >= n) return 0;
	snprintf(out, n, "%s", base);
	return 1;
}

void *vfs_read_save(const char *game_path, int *out_size) {
	if (!game_path || !strstr(game_path, "/Documents/")) return NULL;

	char save_dir[768], base[128], full[1024];
	if (!saves_dir(save_dir, sizeof(save_dir))) return NULL;
	if (!save_basename(game_path, base, sizeof(base))) return NULL;
	snprintf(full, sizeof(full), "%s/%s", save_dir, base);

	// NULL == "this mod has no such save yet". The caller MUST NOT fall
	// back to the shared vanilla save — isolation is the point.
	return vfs_read_file_at(full, out_size);
}

int vfs_write_save(const char *game_path, const void *data, unsigned int size) {
	if (!game_path || !strstr(game_path, "/Documents/")) return 0;

	char save_dir[768], base[128], full[1024];
	if (!saves_dir(save_dir, sizeof(save_dir))) return 0;
	if (!save_basename(game_path, base, sizeof(base))) return 0;
	snprintf(full, sizeof(full), "%s/%s", save_dir, base);

	// Write-then-rename so a crash or full disk mid-write can't leave a
	// truncated save behind.
	char tmp[1040];
	snprintf(tmp, sizeof(tmp), "%s.tmp", full);

	FILE *f = fopen(tmp, "wb");
	if (f == NULL) return 0;
	size_t written = fwrite(data, 1, size, f);
	fclose(f);
	if (written != size) {
		remove(tmp);
		return 0;
	}
	if (rename(tmp, full) != 0) {
		remove(tmp);
		return 0;
	}
	return 1;
}

// ==========================================
// JNI config entrypoints (same signatures as always — Java side untouched)
// ==========================================

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_initPaths(JNIEnv *env, jclass clazz, jstring internalFiles, jstring externalFiles) {
	const char *i = internalFiles ? (*env)->GetStringUTFChars(env, internalFiles, NULL) : NULL;
	const char *e = externalFiles ? (*env)->GetStringUTFChars(env, externalFiles, NULL) : NULL;
	vfs_set_paths(i, e);
	if (i) (*env)->ReleaseStringUTFChars(env, internalFiles, i);
	if (e) (*env)->ReleaseStringUTFChars(env, externalFiles, e);

	// Cache what we need to call back into MainActivity.currentMod() later,
	// from whatever native thread the game ends up loading assets/saves on.
	(*env)->GetJavaVM(env, &g_vm);
	g_mainActivityClass = (jclass)(*env)->NewGlobalRef(env, clazz);
	g_currentModMethod = (*env)->GetStaticMethodID(env, g_mainActivityClass, "currentMod", "()Ljava/lang/String;");
	if (!g_currentModMethod) {
		LOGE("vfs: Couldn't find MainActivity.currentMod() — native sessions will be vanilla");
		(*env)->ExceptionClear(env);
	}
	g_currentInstanceMethod = (*env)->GetStaticMethodID(env, g_mainActivityClass, "currentInstance", "()Ljava/lang/String;");
	if (!g_currentInstanceMethod) {
		LOGE("vfs: Couldn't find MainActivity.currentInstance() — instance-scoped mods will use the global dir");
		(*env)->ExceptionClear(env);
	}

	LOGI("vfs: paths captured -> Internal: %s | External: %s", g_internal_root, g_external_root);
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_setSession(JNIEnv *env, jclass clazz, jstring instanceId, jstring modId) {
	const char *i = instanceId ? (*env)->GetStringUTFChars(env, instanceId, NULL) : NULL;
	const char *m = modId ? (*env)->GetStringUTFChars(env, modId, NULL) : NULL;
	vfs_set_session(i, m);
	if (i) (*env)->ReleaseStringUTFChars(env, instanceId, i);
	if (m) (*env)->ReleaseStringUTFChars(env, modId, m);
}
