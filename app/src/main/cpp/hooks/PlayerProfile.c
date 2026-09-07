#include <stdlib.h>
#include <string.h>
#include "hook.h"
#include "PlayerProfile.h"
#include "stdstring.h"
#include "log.h"

#define LOG_TAG "PlayerProfileHooks"

static char *g_profile_id;

const char* profile_get_id() {
	return g_profile_id;
}

HOOK_SYMBOL(
	LoadGameState,
	"_ZN5Caver13PlayerProfile13LoadGameStateEv",
	void, (PlayerProfile *profile)
) {
	LOGD("Loaded Game State.");
	return orig_LoadGameState(profile);
}

HOOK_SYMBOL(
	Load,
	"_ZN5Caver13PlayerProfile13LoadGameStateEv",
	void, (PlayerProfile *profile)
) {
	LOGD("Loaded.");
	return orig_Load(profile);
}

HOOK_SYMBOL(
	LoadFromPath,
	"_ZN5Caver13PlayerProfile12LoadFromPathERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEb",
	void, (PlayerProfile *profile, String *path, bool unknown)
) {
	LOGD("Path: %s", String_get(path));
	orig_LoadFromPath(profile, path, unknown);
	LOGD("...ID: %s", String_get(&profile->Identifier));
}

HOOK_SYMBOL(
	LoadFromProtobufMessage,
	"_ZN5Caver13PlayerProfile23LoadFromProtobufMessageERKNS_5Proto13PlayerProfileEb",
	void, (void *this, void *message, bool unknown)
) {
	LOGD("Loading from a Protobuf Message...");
	return orig_LoadFromProtobufMessage(this, message, unknown);
}

// The *only* consistent function here...?
HOOK_SYMBOL(
	DidStart,
	"_ZN5Caver22MainMenuViewController38ProfileSelectionViewControllerDidStartEPNS_20ProfileSelectionViewERKN5boost10shared_ptrINS_13PlayerProfileEEE",
	void, (void *this, void *profile_selection_view, void **boost_shared_profile)
) {
	PlayerProfile *profile = *boost_shared_profile;
	const char *id = String_get(&profile->Identifier);
	LOGD("ID: '%s'", id);
	if (g_profile_id) {
		free(g_profile_id);
		g_profile_id = NULL;
	}
	g_profile_id = strdup(id);
	return orig_DidStart(this, profile_selection_view, boost_shared_profile);
}

G_DL_SYMBOL(
	PlayerProfile_Load,
	"_ZN5Caver13PlayerProfile4LoadEv",
	void, (PlayerProfile *profile)
);

G_DL_SYMBOL(
	PlayerProfile_Save,
	"_ZN5Caver13PlayerProfile4SaveEb",
	void, (PlayerProfile *profile, bool unknown)
);

G_DL_SYMBOL(
	PlayerProfile_LoadGameState,
	"_ZN5Caver13PlayerProfile13LoadGameStateEv",
	void, (PlayerProfile *profile)
);

G_DL_SYMBOL(
	PlayerProfile_LoadFromPath,
	"_ZN5Caver13PlayerProfile12LoadFromPathERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEb",
	void, (PlayerProfile *profile, String *path, bool unknown)
);

G_DL_SYMBOL(
	PlayerProfile_UpdateLastPlayedTime,
	"_ZN5Caver13PlayerProfile20UpdateLastPlayedTimeEv",
	void, (PlayerProfile *profile)
);

G_DL_SYMBOL(
	PlayerProfile_ValueForCounter,
	"_ZN5Caver13PlayerProfile15ValueForCounterERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	int, (PlayerProfile *profile, String *name)
);

G_DL_SYMBOL(
	PlayerProfile_SetValueForCounter,
	"_ZN5Caver13PlayerProfile18SetValueForCounterERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEi",
	void, (PlayerProfile *profile, String *name, int value)
);

G_DL_SYMBOL(
	PlayerProfile_IncreaseCounterValue,
	"_ZN5Caver13PlayerProfile20IncreaseCounterValueERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEi",
	void, (PlayerProfile *profile, String *name, int delta)
);
