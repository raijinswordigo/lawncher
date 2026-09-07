#ifndef LAWNCHER_PROGRAMSTATE_H
#define LAWNCHER_PROGRAMSTATE_H

#include "../lua/lua.h"
#include "hook.h"

typedef struct ProgramState {
	lua_State *L;
	struct ProgramState *parent;
	void *tableBegin;
	void *tableEnd;
	char _pad0[archSplit(0x08, 0x10)];
	void *parentObject;
	char _pad1[archSplit(0x08, 0x10)];
	int someFlag;
	char _pad2[archSplit(0x04, 0x08)];
	void *luaRef;
	float speedMultiplier;
	char _pad3[archSplit(0x00, 0x04)];
} ProgramState;

#endif