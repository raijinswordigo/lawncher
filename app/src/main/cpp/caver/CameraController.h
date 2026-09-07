#ifndef LAWNCHER_CAMERACONTROLLER_H
#define LAWNCHER_CAMERACONTROLLER_H

#include "hook.h"
#include "types.h"
#include "Camera.h"
#include "SceneObject.h"
#include "lua.h"

typedef struct CameraController {
	int flags;
	char _pad0[archSplit(0x0c, 0x0c)];
	Vector3 targetPos;
	float lerpFactor;
	Vector3 currentPos;
	float zoom;
	Vector3 focusPos;
	char _pad1[archSplit(0x14, 0x1c)];
	Camera *camera;
	void *cameraRef;
	char _pad2[archSplit(0x00, 0x08)];
	SceneObject *followObject;
	void *followObjectRef;
	Vector3 followOffset;
	char _pad3[archSplit(0x10, 0x18)];
	float rumble;
	char _pad4[archSplit(0x00, 0x04)];
} CameraController;

CameraController *cameraController_from_L(lua_State *L);
CameraController *cameraController_get();

DL_SYMBOL_DECL(CameraController_Update, void, (CameraController *cc, float dt));
DL_SYMBOL_DECL(CameraController_FollowObject, void, (CameraController *cc, void *intrusive_object, Vector3 *offset));
DL_SYMBOL_DECL(CameraController_StopFollowing, void, (CameraController *cc));
DL_SYMBOL_DECL(CameraController_FocusAtPoint, void, (CameraController *cc, Vector3 *point, bool immediate));
DL_SYMBOL_DECL(CameraController_GotoTargetImmediately, void, (CameraController *cc));
DL_SYMBOL_DECL(CameraController_ResetFocus, void, (CameraController *cc));
DL_SYMBOL_DECL(CameraController_Rumble, void, (CameraController *cc));

#endif
