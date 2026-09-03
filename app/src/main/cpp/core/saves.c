#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"
#include "PlayerProfile.h"
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <stdio.h>
#include <dirent.h>
#include <errno.h>

#define LOG_TAG "SaveManager"
#define GET_PID(profile) String_get(&(profile)->Identifier)

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
	return path && strstr(path, "/Documents") != NULL;
}

static int redirect_to_mod_saves(const char *path, char *out, size_t out_size) {
	char saves[512];
	if (!mod_saves_dir(saves, sizeof(saves))) return 0;
	const char *base = path_basename(path);
	if (strcmp(base, "Documents") == 0) {
		snprintf(out, out_size, "%s", saves);
	} else {
		snprintf(out, out_size, "%s/%s", saves, base);
	}
	return 1;
}

HOOK_SYMBOL(
	NewByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEEPj",
	void*, (String *spath, uint *param_2)
) {
	const char *path = String_get(spath);
	if (!is_documents_path(path)) return orig_NewByteBufferFromFile(spath, param_2);

	char newpath[600];
	if (!redirect_to_mod_saves(path, newpath, sizeof(newpath))) return orig_NewByteBufferFromFile(spath, param_2);

	LOGD("load %s -> %s", path, newpath);
	String newstr;
	String_create(&newstr, newpath);
	return orig_NewByteBufferFromFile(&newstr, param_2);
}

HOOK_SYMBOL(
	SaveByteBufferToFile,
	"_ZN5Caver20SaveByteBufferToFileEPKhjRKNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE",
	uint, (unsigned char *param_1, uint param_2, String *spath)
) {
	const char *path = String_get(spath);
	if (!is_documents_path(path)) return orig_SaveByteBufferToFile(param_1, param_2, spath);

	char newpath[600];
	if (!redirect_to_mod_saves(path, newpath, sizeof(newpath))) return orig_SaveByteBufferToFile(param_1, param_2, spath);

	LOGD("save %s -> %s", path, newpath);
	String newstr;
	String_create(&newstr, newpath);
	return orig_SaveByteBufferToFile(param_1, param_2, &newstr);
}

typedef struct {
	String *begin;
	String *end;
	String *end_cap;
} vector;

HOOK_SYMBOL(
	GetFilesWithExt,
	"_ZN5Caver21GetFilesWithExtensionERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_PNS0_6vectorIS6_NS4_IS6_EEEE",
	void, (String *sext, String *spat, vector *out)
) {
	const char *path = String_get(spat);
	if (!is_documents_path(path)) {
		orig_GetFilesWithExt(sext, spat, out);
		return;
	}

	char saves[512];
	if (!mod_saves_dir(saves, sizeof(saves))) {
		orig_GetFilesWithExt(sext, spat, out);
		return;
	}

	LOGD("list %s -> %s", path, saves);
	String newp;
	String_create(&newp, saves);
	orig_GetFilesWithExt(sext, &newp, out);
}

HOOK_SYMBOL(
	FileExistsAtPath,
	"_ZN5Caver16FileExistsAtPathERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE",
	uint, (String *spath)
) {
	const char *path = String_get(spath);
	if (!is_documents_path(path)) return orig_FileExistsAtPath(spath);

	char newpath[600];
	if (!redirect_to_mod_saves(path, newpath, sizeof(newpath))) return orig_FileExistsAtPath(spath);

	LOGD("exists %s -> %s", path, newpath);
	String newstr;
	String_create(&newstr, newpath);
	return orig_FileExistsAtPath(&newstr);
}
