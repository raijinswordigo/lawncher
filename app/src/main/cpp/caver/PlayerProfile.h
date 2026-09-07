#ifndef LAWNCHER_PLAYERPROFILE_H
#define LAWNCHER_PLAYERPROFILE_H

#include "hook.h"
#include "stdstring.h"

typedef struct PlayerProfile {
	char _pad0[archSplit(0x0c, 0x18)];
	String Identifier;
	char _pad1[archSplit(0x20, 0x28)];
	void *lastPlayedTime; // uses DateTime, I need to decompile that later
	char _pad2[archSplit(0x40, 0x50)];
	int someFlag;
	char _pad3[archSplit(0x28, 0x30)];
	void *countersBegin;
	void *countersEnd;
	char _pad4[archSplit(0x10, 0x18)];
} PlayerProfile;

const char* profile_get_id();

DL_SYMBOL_DECL(PlayerProfile_Load, void, (PlayerProfile *profile));
DL_SYMBOL_DECL(PlayerProfile_Save, void, (PlayerProfile *profile, bool unknown));
DL_SYMBOL_DECL(PlayerProfile_LoadGameState, void, (PlayerProfile *profile));
DL_SYMBOL_DECL(PlayerProfile_LoadFromPath, void, (PlayerProfile *profile, String *path, bool unknown));
DL_SYMBOL_DECL(PlayerProfile_UpdateLastPlayedTime, void, (PlayerProfile *profile));
DL_SYMBOL_DECL(PlayerProfile_ValueForCounter, int, (PlayerProfile *profile, String *name));
DL_SYMBOL_DECL(PlayerProfile_SetValueForCounter, void, (PlayerProfile *profile, String *name, int value));
DL_SYMBOL_DECL(PlayerProfile_IncreaseCounterValue, void, (PlayerProfile *profile, String *name, int delta));

#endif //LAWNCHER_PLAYERPROFILE_H
