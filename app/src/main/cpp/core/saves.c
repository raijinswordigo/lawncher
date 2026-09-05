#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"
#include "PlayerProfile.h"
#include <stdlib.h>
#include <sys/stat.h>
#include <dirent.h>
#include <string.h>
#include <stdio.h>
#define LOG_TAG "SaveManager"
#define GET_PID(profile) String_get(&(profile)->Identifier)
typedef struct {
	void* ptr;
	void* idk;
} boost_shared_ptr;
//typedef struct {
//	boost_shared_ptr* begin;
//	boost_shared_ptr* end;
//	boost_shared_ptr* end_cap;
//} vector;
typedef struct {
	String *begin;
	String *end;
	String *end_cap;
} vector;
static void vector_init(vector *vec) {
	if (!vec) return;
	vec->begin = NULL;
	vec->end = NULL;
	vec->end_cap = NULL;
}
static void vector_push_back(vector *vec, String element) {
	if (!vec) return;
	if (vec->end == vec->end_cap) {
		size_t current_capacity = vec->end_cap - vec->begin;
		size_t new_capacity = current_capacity == 0 ? 4 : current_capacity * 2;
		size_t current_size = vec->end - vec->begin;
		String *new_buffer = realloc(vec->begin, new_capacity * sizeof(String));
		if (!new_buffer) return;
		vec->begin = new_buffer;
		vec->end = new_buffer + current_size;
		vec->end_cap = new_buffer + new_capacity;
	}
	*vec->end = element;
	vec->end++;
}
static const char *path_basename(const char *path) {
	const char *slash = strrchr(path, '/');
	return slash ? slash + 1 : path;
}
HOOK_SYMBOL(
	AddProfile,
	"_ZN5Caver14ProfileManager10AddProfileERKN5boost10shared_ptrINS_13PlayerProfileEEE",
	void, (void *this, PlayerProfile **profile)
) {
	LOGD("Adding Profile: %s", GET_PID(*profile));
	return orig_AddProfile(this, profile);
}
static void ensure_dir(const char *path) {
	// recursive like assets, parents matter
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
HOOK_SYMBOL(
	DocumentsDirectoryPath,
	"_ZN5Caver22DocumentsDirectoryPathEv",
	void, (void)
) {
	// x8 = sret out pointer (caller-saved). capture it, then call orig
	// with a raw blr so nothing the C compiler does can trash x8 / lr.
	String *out;
	asm volatile("mov %0, x8" : "=r"(out));

	asm volatile(
		"mov x8, %0\n"
		"blr %1"
		:
		: "r"(out), "r"(orig_DocumentsDirectoryPath)
		: "x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7",
	"x9", "x10", "x11", "x12", "x13", "x14", "x15",
	"x16", "x17", "x18", "memory", "cc"
		);

	const char *id = java_current_mod_id();
	if (id && *id) {
		const char *savesdir = java_resource_path("saves/");
		ensure_dir(savesdir);
		LOGD("yo %s", savesdir);
		String_destroy(out);
		String_create(out, savesdir);
	}
}
/*
HOOK_SYMBOL(
	GetAllProfiles,
	"_ZN5Caver14ProfileManager14GetAllProfilesEPNSt6__ndk16vectorIN5boost10shared_ptrINS_13PlayerProfileEEENS1_9allocatorIS6_EEEE",
	void, (void *this, vector *outprofiles)
	) {
	LOGD("GetAllProfiles called!");
//	orig_GetAllProfiles(this, outprofiles);
//	size_t count = outprofiles->end - outprofiles->begin;
//	LOGD("Received %zu profiles", count);
//	int n = 0;
//	for (boost_shared_ptr *it = outprofiles->begin; it != outprofiles->end; ++it) {
//		n++;
//		PlayerProfile *profile = (PlayerProfile *)it->ptr;
//		LOGD("%d. %s", n, GET_PID(profile));
//	}
	const char *savesdir = java_resource_path("saves/");
	ensure_dir(savesdir);
	LOGD("savesdir = %s", savesdir);
	// check for saves in savesdir rather than vanillas documents path
	// need to also call AddProfile respectively (nah i dont think so)
	DIR *dir = opendir(savesdir);
	struct dirent *entry;
	while ((entry = readdir(dir)) != NULL) {
		if ((strcmp(entry->d_name, ".") == 0) || (strcmp(entry->d_name, "..") == 0)) continue;
		LOGD("entry = %s", entry->d_name);
	}
//	vector_init()
}
*/ // oh fuck nah this function is huge its just better to patch GetFilesWithExtension((basic_string *)&local_98,local_b0,(vector *)&local_80);
/*
HOOK_SYMBOL(
	GetFilesWithExtension,
	"_ZN5Caver21GetFilesWithExtensionERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_PNS0_6vectorIS6_NS4_IS6_EEEE",
	void, (String *extension, String *path, vector *outfiles)
) {
	if ((strcmp(String_get(extension), "gplayer") != 0)) goto fallback;
	LOGD("Path = %s, Ext = %s", String_get(path), String_get(extension));
//	orig_GetFilesWithExtension(extension, path, outfiles);
//	int n = 0;
//	for (boost_shared_ptr *it = outfiles->begin; it != outfiles->end; ++it) {
//		n++;
//		String *file = (String *)it->ptr;
//		LOGD("%d. %s", n, String_get(file));
//	}
	// So I basically need to put outfiles in outfiles thing dalright
	// nvm
	const char *id = java_current_mod_id();
	if (!id || !*id) goto fallback;
	const char *savesdir = java_resource_path("saves/");
	ensure_dir(savesdir);
	LOGD("redirecting gplayer list to %s", savesdir);
	String modpath;
	String_create(&modpath, savesdir);
	orig_GetFilesWithExtension(extension, &modpath, outfiles);
	String_destroy(&modpath);
	return;
	DIR *dir = opendir(savesdir);
	if (!dir) {
		LOGE("opendir failed for %s", savesdir);
		goto fallback;
	}
	struct dirent *entry;
	int size_t count = 0;
	while ((entry = readdir(dir)) != NULL) {
		if ((strcmp(entry->d_name, ".") == 0) || (strcmp(entry->d_name, "..") == 0)) continue;
		const char *fname = entry->d_name;
		if (strstr(fname, "gplayer") == NULL) continue;
		LOGD("Actual enttry = %s", fname);
		count++;
		// populate vector later
	}
	closedir(dir);
	outfiles->begin = malloc(count * sizeof(String));
	outfiles->end = outfiles->begin;
	outfiles->end_cap = outfiles->begin + count;
	dir = opendir(savesdir);
	if (!dir) {
		LOGE("opendir failed second time");
		goto fallback;
	}
	while ((entry = readdir(dir)) != NULL) {
		if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
		if (strstr(entry->d_name, "gplayer") == NULL) continue;
//		String out;
//		String_create(&out, entry->d_name);
		if (outfiles->end >= outfiles->end_cap) break;
		String_create((String *)outfiles->end, entry->d_name);
		outfiles->end++;
	}
	closedir(dir);
	return;
	fallback:
	return orig_GetFilesWithExtension(extension, path, outfiles);
}
*/
/*
HOOK_SYMBOL(
	NewByteBufferFromFile,
	"_ZN5Caver21NewByteBufferFromFileERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEEPj",
	void*, (String *path, uint *param_2)
) {
	const char *p = String_get(path);
	if (strstr(p, "gplayer") == NULL) return orig_NewByteBufferFromFile(path, param_2);
	const char *id = java_current_mod_id();
	if (!id || !*id) return orig_NewByteBufferFromFile(path, param_2);
	const char *base = path_basename(p);
	char full[512];
	snprintf(full, sizeof(full), "%s%s", java_resource_path("saves/"), base);
	LOGD("gplayer = %s -> %s", p, full);
	String s;
	String_create(&s, full);
	void *ret = orig_NewByteBufferFromFile(&s, param_2);
	String_destroy(&s);
	return ret;
}*/
// justincase
void saves_on_mod_exit(void) {
	java_reset_mod_id();
	LOGI("saves_on_mod_exit: state cleared");
}
void init_saves(void) {
	LOGI("Start of Save Override");
}