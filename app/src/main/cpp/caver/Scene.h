#ifndef LAWNCHER_SCENE_H
#define LAWNCHER_SCENE_H

#include "hook.h"
#include "ProgramState.h"
#include "stdstring.h"
#include "types.h"
#include "Camera.h"

typedef struct Scene Scene; // TODO: needs to be remapped!!
// hitboxes should be at 0x1c4,

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
