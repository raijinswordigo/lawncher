#ifndef LAWNCHER_SCENE_H
#define LAWNCHER_SCENE_H

#include "hook.h"
#include "ProgramState.h"
#include "stdstring.h"

typedef struct Scene {
	void *vtable;
	char _pad0[archSplit(0x0c, 0x18)];
	int pauseCount;
	char _pad1[archSplit(0x04, 0x04)];
	ProgramState ProgramState;
	char _pad2[archSplit(0x18, 0x20)];
	void *ObjectLibrary;
	char _pad3[archSplit(0x88, 0x108)];
	void *Camera;
	char _pad4[archSplit(0x100, 0x1a0)];
	bool debugHitboxes;
	char _pad5[archSplit(0x8f, 0xaf)];
} Scene;

Scene *scene_from_L(lua_State *L);
Scene *scene_get();

DL_SYMBOL_DECL(Scene_SetPaused, void, (Scene *scene, bool paused));
DL_SYMBOL_DECL(Scene_ObjectWithIdentifier, void*, (Scene *scene, String *id));
DL_SYMBOL_DECL(Scene_AddObject, void, (Scene *scene, void *intrusive_object));
DL_SYMBOL_DECL(Scene_RemoveObject, void, (Scene *scene, void *intrusive_object, bool unknown));
DL_SYMBOL_DECL(Scene_ActivateObject, void, (Scene *scene, void *intrusive_object));
DL_SYMBOL_DECL(Scene_GetAllObjects, void, (Scene *scene, void *vector));
DL_SYMBOL_DECL(Scene_Update, void, (Scene *scene, float dt));

#endif
