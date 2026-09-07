#include "CameraController.h"

static CameraController *g_cc = NULL;

CameraController *cameraController_from_L(lua_State *L) {
	lua_getglobal(L, "cameraController");
	if (!lua_islightuserdata(L, -1)) return NULL;
	const void *cc = lua_topointer(L, -1);
	lua_pop(L, 1);
	return (CameraController *)cc;
}

CameraController *cameraController_get() {
	return g_cc;
}

HOOK_SYMBOL(
	Update,
	"_ZN5Caver16CameraController6UpdateEf",
	void, (CameraController *cc, float dt)
) {
	g_cc = cc;
	return orig_Update(cc, dt);
}

G_DL_SYMBOL(
	CameraController_Update,
	"_ZN5Caver16CameraController6UpdateEf",
	void, (CameraController *cc, float dt)
)

G_DL_SYMBOL(
	CameraController_FollowObject,
	"_ZN5Caver16CameraController12FollowObjectERKN5boost13intrusive_ptrINS_11SceneObjectEEERKNS_7Vector3E",
	void, (CameraController *cc, void *intrusive_object, Vector3 *offset)
)

G_DL_SYMBOL(
	CameraController_StopFollowing,
	"_ZN5Caver16CameraController13StopFollowingEv",
	void, (CameraController *cc)
)

G_DL_SYMBOL(
	CameraController_FocusAtPoint,
	"_ZN5Caver16CameraController12FocusAtPointERKNS_7Vector3Eb",
	void, (CameraController *cc, Vector3 *point, bool immediate)
)

G_DL_SYMBOL(
	CameraController_GotoTargetImmediately,
	"_ZN5Caver16CameraController21GotoTargetImmediatelyEv",
	void, (CameraController *cc)
)

G_DL_SYMBOL(
	CameraController_ResetFocus,
	"_ZN5Caver16CameraController10ResetFocusEv",
	void, (CameraController *cc)
)

G_DL_SYMBOL(
	CameraController_Rumble,
	"_ZN5Caver16CameraController6RumbleEv",
	void, (CameraController *cc)
)
