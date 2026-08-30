#include "hook.h"
#include "java.h"
#include "core.h"
#include "log.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <errno.h>

#define LOG_TAG "LawncherAssets"

static const char *path_basename(const char *path) {
	const char *slash = strrchr(path, '/');
	return slash ? slash + 1 : path;
}

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

static int copy_file(const char *src, const char *dst) {
	FILE *in = fopen(src, "rb");
	if (!in) return 0;

	FILE *out = fopen(dst, "wb");
	if (!out) {
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

static void seed_mod_saves_from_vanilla(const char *savesDir) {
	const char *internal = java_internal_files();
	if (!internal[0]) return;

	char vanillaDocs[300];
	snprintf(vanillaDocs, sizeof(vanillaDocs), "%s/Documents", internal);

	DIR *dir = opendir(vanillaDocs);
	if (!dir) {
		LOGI("no vanilla Documents yet, skip seed");
		return;
	}

	int copied = 0;
	struct dirent *entry;
	while ((entry = readdir(dir)) != NULL) {
		if (entry->d_name[0] == '.') continue;

		char src[512], dst[512];
		snprintf(src, sizeof(src), "%s/%s", vanillaDocs, entry->d_name);
		snprintf(dst, sizeof(dst), "%s/%s", savesDir, entry->d_name);

		struct stat st;
		if (stat(src, &st) != 0 || !S_ISREG(st.st_mode)) continue;

		if (copy_file(src, dst)) {
			copied++;
			LOGI("seeded %s", entry->d_name);
		} else {
			LOGE("failed to seed %s", entry->d_name);
		}
	}
	closedir(dir);
	LOGI("seeded %d file(s) into %s", copied, savesDir);
}

static int g_seed_done = 0;

static int mod_saves_dir(char *out, size_t out_size) {
	const char *mod = java_current_mod_id();
	const char *external = java_external_files();

	if (!mod[0] || !external[0]) return 0;

	if (out && out_size > 0) {
		snprintf(out, out_size, "%s/mods/%s/saves", external, mod);
		ensure_dir(out);

		if (!g_seed_done) {
			seed_mod_saves_from_vanilla(out);
			g_seed_done = 1;
		}
	}
	return 1;
}

static int is_documents_path(const char *path) {
	return path && strstr(path, "/Documents/") != NULL;
}

static void make_mod_save_path(char *out, size_t out_size, const char *original) {
	char saveDir[512];
	mod_saves_dir(saveDir, sizeof(saveDir));
	snprintf(out, out_size, "%s/%s", saveDir, path_basename(original));
}

static __thread int g_redirect_opendir = 0;
static __thread char g_mod_saves_for_opendir[512];

static DIR *(*orig_opendir)(const char *path) = NULL;

static DIR *hook_opendir(const char *path) {
	if (g_redirect_opendir && g_mod_saves_for_opendir[0]) {
		LOGI("opendir %s -> %s", path, g_mod_saves_for_opendir);
		return orig_opendir(g_mod_saves_for_opendir);
	}
	return orig_opendir(path);
}

__attribute__((constructor))
static void register_opendir_hook(void) {
	GlossInit(true);
	GlossHookByName("libc.so", "opendir",
	                (void *)hook_opendir, (void **)&orig_opendir, NULL);
}

/* called from Java when we leave a mod / return to launcher */
void assets_on_mod_exit(void) {
	g_seed_done = 0;
	g_redirect_opendir = 0;
	g_mod_saves_for_opendir[0] = '\0';
	java_reset_mod_id();
	LOGI("assets_on_mod_exit: state cleared");
}

HOOK_SYMBOL(
	ByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKSsPj",
	void *, (char **path, int *n)
) {
	const char *file_path = *path;
	LOGD("%s", file_path);

	if (is_documents_path(file_path) && mod_saves_dir(NULL, 0)) {
		char redirected[600];
		make_mod_save_path(redirected, sizeof(redirected), file_path);

		FILE *f = fopen(redirected, "rb");
		if (f) {
			fseek(f, 0, SEEK_END);
			long size = ftell(f);
			fseek(f, 0, SEEK_SET);

			if (size > 0) {
				void *buffer = malloc(size);
				if (buffer) {
					size_t nread = fread(buffer, 1, size, f);
					fclose(f);
					if (nread == (size_t)size) {
						LOGI("loaded mod save %s", redirected);
						if (n) *n = (int)size;
						return buffer;
					}
					LOGE("short read %s", redirected);
					free(buffer);
				} else {
					LOGE("oom for %s (%ld)", redirected, size);
					fclose(f);
				}
			} else {
				fclose(f);
			}
		}
		LOGI("no mod save at %s", redirected);
		return NULL;
	}

	return orig_ByteBufferFromFile(path, n);
}

HOOK_SYMBOL(
	SaveByteBufferToFile,
	"_ZN5Caver20SaveByteBufferToFileEPKhjRKSs",
	int, (void *data, unsigned int size, char **path)
) {
	const char *file_path = *path;

	if (is_documents_path(file_path) && mod_saves_dir(NULL, 0)) {
		char redirected[600];
		make_mod_save_path(redirected, sizeof(redirected), file_path);

		char tmp[620];
		snprintf(tmp, sizeof(tmp), "%s.tmp", redirected);

		FILE *f = fopen(tmp, "wb");
		if (f) {
			size_t written = fwrite(data, 1, size, f);
			fclose(f);

			if (written == size) {
				if (rename(tmp, redirected) == 0) {
					LOGI("saved mod save %s (%u)", redirected, size);
					return 1;
				}
				LOGE("rename failed %s -> %s", tmp, redirected);
				remove(tmp);
			} else {
				LOGE("short write %s (%zu/%u)", tmp, written, size);
				remove(tmp);
			}
		} else {
			LOGE("open failed %s", tmp);
		}
	}

	return orig_SaveByteBufferToFile(data, size, path);
}

HOOK_SYMBOL(
	NewByteBuffer,
	"_ZN5Caver29NewByteBufferFromAndroidAssetERKSsPj",
	void *, (char **path, int *n)
) {
	const char *asset_path = *path;
	const char *mod = java_current_mod_id();
	const char *external = java_external_files();

	if (asset_path && mod[0] && external[0]) {
		char full_path[512];
		snprintf(full_path, sizeof(full_path), "%s/mods/%s/%s",
		         external, mod, asset_path);

		FILE *f = fopen(full_path, "rb");
		if (f) {
			fseek(f, 0, SEEK_END);
			long size = ftell(f);
			fseek(f, 0, SEEK_SET);

			if (size > 0) {
				void *buffer = malloc(size);
				if (buffer) {
					size_t nread = fread(buffer, 1, size, f);
					fclose(f);
					if (nread == (size_t)size) {
						LOGI("override %s from mod '%s'", asset_path, mod);
						if (n) *n = (int)size;
						return buffer;
					}
					LOGE("short read %s", full_path);
					free(buffer);
				} else {
					LOGE("oom for %s (%ld)", full_path, size);
					fclose(f);
				}
			} else {
				fclose(f);
			}
		}
	}

	return orig_NewByteBuffer(path, n);
}

HOOK_SYMBOL(
	FileExistsAtPath,
	"_ZN5Caver16FileExistsAtPathERKSs",
	unsigned int, (char **path)
) {
	const char *file_path = *path;

	if (is_documents_path(file_path) && mod_saves_dir(NULL, 0)) {
		char redirected[600];
		make_mod_save_path(redirected, sizeof(redirected), file_path);

		struct stat st;
		if (stat(redirected, &st) == 0 && S_ISREG(st.st_mode)) {
			LOGI("exists in mod saves: %s", redirected);
			return 1;
		}
		LOGI("missing in mod saves: %s", redirected);
		return 0;
	}

	return orig_FileExistsAtPath(path);
}

HOOK_SYMBOL(
	GetFilesWithExtension,
	"_ZN5Caver21GetFilesWithExtensionERKSsS1_PSt6vectorISsSaISsEE",
	void, (void *ext, void *dir, void *out_vector)
) {
	const char *extension = *(const char **)ext;

	if (extension && strstr(extension, "gplayer") &&
	    mod_saves_dir(g_mod_saves_for_opendir, sizeof(g_mod_saves_for_opendir))) {

		LOGI("GetFilesWithExtension: redirect opendir -> %s", g_mod_saves_for_opendir);
		g_redirect_opendir = 1;
		orig_GetFilesWithExtension(ext, dir, out_vector);
		g_redirect_opendir = 0;
		g_mod_saves_for_opendir[0] = '\0';
		return;
	}

	orig_GetFilesWithExtension(ext, dir, out_vector);
}

HOOK_SYMBOL(
	DeleteFileAtPath,
	"_ZN5Caver16DeleteFileAtPathERKSs",
	void, (char **path)
) {
	const char *file_path = *path;

	if (is_documents_path(file_path) && mod_saves_dir(NULL, 0)) {
		char redirected[600];
		make_mod_save_path(redirected, sizeof(redirected), file_path);

		if (remove(redirected) == 0)
			LOGI("deleted mod save %s", redirected);
		else
			LOGI("no mod save at %s (errno=%d)", redirected, errno);
	}

	orig_DeleteFileAtPath(path);
}

void init_assets(void) {
	LOGI("asset overrides active!!!");
}