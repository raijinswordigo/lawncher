#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"
#include "PlayerProfile.h"
#include <stdlib.h>
#include <sys/stat.h>
#include <dirent.h>
#include <string.h>

#define LOG_TAG "SaveManager"
#define GET_PID(profile) String_get(&(profile)->Identifier)

typedef struct {
	void* ptr;
	void* idk;
} boost_shared_ptr;

typedef struct {
	boost_shared_ptr* begin;
	boost_shared_ptr* end;
	boost_shared_ptr* end_cap;
} vector;

static void vector_init(vector *vec) {
	if (!vec) return;
	vec->begin = NULL;
	vec->end = NULL;
	vec->end_cap = NULL;
}

static void vector_push_back(vector *vec, boost_shared_ptr element) {
	if (!vec) return;
	if (vec->end == vec->end_cap) {
		size_t current_capacity = vec->end_cap - vec->begin;
		size_t new_capacity = current_capacity == 0 ? 4 : current_capacity * 2;
		size_t current_size = vec->end - vec->begin;

		boost_shared_ptr *new_buffer = realloc(vec->begin, new_capacity * sizeof(boost_shared_ptr));
		if (!new_buffer) return;

		vec->begin = new_buffer;
		vec->end = new_buffer + current_size;
		vec->end_cap = new_buffer + new_capacity;
	}
	*vec->end = element;
	vec->end++;
}

HOOK_SYMBOL(
	AddProfile,
	"_ZN5Caver14ProfileManager10AddProfileERKN5boost10shared_ptrINS_13PlayerProfileEEE",
	void, (void *this, PlayerProfile **profile)
	) {
	LOGD("Adding Profile: %s", GET_PID(*profile));
	return orig_AddProfile(this, profile);
}

static void ensure_dir(const char *dir) {
	mkdir(dir, 0755);
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

HOOK_SYMBOL(
	GetFilesWithExtension,
	"_ZN5Caver21GetFilesWithExtensionERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_PNS0_6vectorIS6_NS4_IS6_EEEE",
	void, (String *extension, String *path, vector *outfiles)
	) {
	if (strcmp(String_get(extension), "gplayer") == 0) goto fallback;
	LOGD("Path = %s, Ext = %s", String_get(path), String_get(extension));
//	orig_GetFilesWithExtension(extension, path, outfiles);
//	int n = 0;
//	for (boost_shared_ptr *it = outfiles->begin; it != outfiles->end; ++it) {
//		n++;
//		String *file = (String *)it->ptr;
//		LOGD("%d. %s", n, String_get(file));
//	}
	// So I basically need to put outfiles in outfiles thing dalright


	fallback:
	return orig_GetFilesWithExtension(extension, path, outfiles);
}