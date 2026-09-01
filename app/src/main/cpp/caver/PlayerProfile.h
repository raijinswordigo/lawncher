#ifndef LAWNCHER_PLAYERPROFILE_H
#define LAWNCHER_PLAYERPROFILE_H

#include "../core/hook.h"

typedef struct PlayerProfile {
	char _pad0[archSplit(0x0c, 0x18)];
	String Identifier;
} PlayerProfile;

#endif //LAWNCHER_PLAYERPROFILE_H
