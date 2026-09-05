#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <stdio.h>

#define LOG_TAG "SaveManager"

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

static const char *path_basename(const char *path) {
	const char *s = strrchr(path, '/');
	return s ? s + 1 : path;
}

static int is_save_ext(const char *ext) {
	return !strcmp(ext, "gplayer");
}

static int is_save_path(const char *p) {
	return p && strstr(p, ".gplayer");
}

static void redirect_path(String *out, const char *orig) {
	const char *id = java_current_mod_id();
	if (!id || !*id) {
		String_create(out, orig);
		return;
	}
	const char *base = path_basename(orig);
	char full[512];
	snprintf(full, sizeof(full), "%s%s", java_resource_path("saves/"), base);
	LOGD("redirect %s -> %s", orig, full);
	String_create(out, full);
}

HOOK_SYMBOL(
	GetFilesWithExtension,
	"_ZN5Caver21GetFilesWithExtensionERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_PNS0_6vectorIS6_NS4_IS6_EEEE",
	void, (String *extension, String *path, void *outfiles)
) {
	const char *ext = String_get(extension);
	const char *p = String_get(path);
	LOGD("GetFilesWithExtension ext=%s path=%s", ext, p);
	const char *id = java_current_mod_id();
	if (id && *id && is_save_ext(ext)) {
		const char *savesdir = java_resource_path("saves/");
		ensure_dir(savesdir);
		String modpath;
		String_create(&modpath, savesdir);
		orig_GetFilesWithExtension(extension, &modpath, outfiles);
		String_destroy(&modpath);
		return;
	}
	orig_GetFilesWithExtension(extension, path, outfiles);
}

HOOK_SYMBOL(
	NewByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEEPj",
	void*, (String *path, uint *out_size)
) {
	const char *p = String_get(path);
	LOGD("NewByteBufferFromFile %s", p);
	if (!is_save_path(p)) return orig_NewByteBufferFromFile(path, out_size);
	String s;
	redirect_path(&s, p);
	void *ret = orig_NewByteBufferFromFile(&s, out_size);
	String_destroy(&s);
	return ret;
}

HOOK_SYMBOL(
	SaveByteBufferToFile,
	"_ZN5Caver20SaveByteBufferToFileEPKhjRKNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE",
	uint, (const unsigned char *buf, uint size, String *path)
) {
	const char *p = String_get(path);
	LOGD("SaveByteBufferToFile %s size=%u", p, size);
	if (!is_save_path(p)) return orig_SaveByteBufferToFile(buf, size, path);

	const char *id = java_current_mod_id();
	if (!id || !*id) return orig_SaveByteBufferToFile(buf, size, path);

	const char *base = path_basename(p);
	char full[512];
	snprintf(full, sizeof(full), "%s%s", java_resource_path("saves/"), base);
	ensure_dir(java_resource_path("saves/"));
	LOGD("writing ourselves to %s", full);

	FILE *f = fopen(full, "wb");
	if (!f) {
		LOGE("fopen failed for %s", full);
		return 0;
	}
	size_t written = fwrite(buf, 1, size, f);
	fclose(f);
	LOGD("wrote %zu bytes", written);
	return written == size ? 1 : 0;
}

HOOK_SYMBOL(
	FileExistsAtPath,
	"_ZN5Caver16FileExistsAtPathERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE",
	uint, (String *path)
) {
	const char *p = String_get(path);
	LOGD("FileExistsAtPath %s", p);
	if (!is_save_path(p)) return orig_FileExistsAtPath(path);
	String s;
	redirect_path(&s, p);
	uint ret = orig_FileExistsAtPath(&s);
	String_destroy(&s);
	return ret;
}

HOOK_SYMBOL(
	DeleteFileAtPath,
	"_ZN5Caver16DeleteFileAtPathERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE",
	bool, (String *path)
) {
	const char *p = String_get(path);
	LOGD("DeleteFileAtPath %s", p);
	if (!is_save_path(p)) return orig_DeleteFileAtPath(path);
	String s;
	redirect_path(&s, p);
	bool ret = orig_DeleteFileAtPath(&s);
	String_destroy(&s);
	return ret;
}

void saves_on_mod_exit(void) {
	java_reset_mod_id();
	LOGI("saves_on_mod_exit: state cleared");
}

void init_saves(void) {
	LOGI("Save Override ready");
}