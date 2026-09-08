#ifndef LAWNCHER_SCENEOBJECT_H
#define LAWNCHER_SCENEOBJECT_H

#include "hook.h"
#include "stdstring.h"
#include "types.h"

typedef struct SceneObject {
	void *vtable;
	int refCount;
	char _pad0[archSplit(0x08, 0x10)];
	void *Scene;
	char _pad1[archSplit(0x04, 0x08)];
	struct SceneObject *Parent;
	char _pad2[archSplit(0x04, 0x08)];
	float Scaling;
	char _pad3[archSplit(0x0c, 0x0c)];
	String Identifier;
	char _pad4[archSplit(0x04, 0x18)];
	Vector3 Position;
	char _pad5[archSplit(0x0c, 0x0c)];
	Rectangle LocalAABB;
	char _pad6[archSplit(0x5c, 0x50)];
	bool Hidden;
	char _pad7[archSplit(0x27, 0x33)];
} SceneObject;

DL_SYMBOL_DECL(SceneObject_setPosition, void, (SceneObject *obj, Vector2 *pos));
DL_SYMBOL_DECL(SceneObject_setLocalAABB, void, (SceneObject *obj, Rectangle *aabb));
DL_SYMBOL_DECL(SceneObject_SetIdentifier, void, (SceneObject *obj, String *id));
DL_SYMBOL_DECL(SceneObject_Activate, void, (SceneObject *obj));
DL_SYMBOL_DECL(SceneObject_isHidden, bool, (SceneObject *obj));
DL_SYMBOL_DECL(SceneObject_UpdateBounds, void, (SceneObject *obj));
DL_SYMBOL_DECL(SceneObject_SetAlwaysActive, void, (SceneObject *obj, bool active));
DL_SYMBOL_DECL(SceneObject_SetInstanceScaling, void, (SceneObject *obj, float scale));
DL_SYMBOL_DECL(SceneObject_AddComponent, void, (SceneObject *obj, void *comp));
DL_SYMBOL_DECL(SceneObject_RemoveAllComponents, void, (SceneObject *obj));
DL_SYMBOL_DECL(SceneObject_ComponentWithInterface, void*, (SceneObject *obj, void* interface));
DL_SYMBOL_DECL(SceneObject_Update, void, (SceneObject *obj, float dt, bool a, bool b));

#endif
