#include "hook.h"
#include "lua.h"
#include "lauxlib.h"
#include "components.h"
#include "main/SceneObject.h"

DL_SYMBOL(
	StopMoving,
	"_ZN5Caver27CharAnimControllerComponent10StopMovingEv",
	void, (void* this)
);

DL_SYMBOL(
	StopAction,
	"_ZN5Caver27CharAnimControllerComponent10StopActionEv",
	void, (void* this)
);

DL_SYMBOL(
	StartMoving,
	"_ZN5Caver27CharAnimControllerComponent11StartMovingEv",
	void, (void* this)
);

DL_SYMBOL(
	BeginCasting,
	"_ZN5Caver27CharAnimControllerComponent12BeginCastingEv",
	void, (void* this)
);

DL_SYMBOL(
	StartFalling,
	"_ZN5Caver27CharAnimControllerComponent12StartFallingEv",
	void, (void* this)
);

DL_SYMBOL(
	IsReadyToJump,
	"_ZN5Caver27CharAnimControllerComponent13IsReadyToJumpEv",
	bool, (void* this)
);

DL_SYMBOL(
	ActionNearlyFinished,
	"_ZN5Caver27CharAnimControllerComponent13IsReadyToJumpEv",
	bool, (void* this)
);

DL_SYMBOL(
	IsMoving,
	"_ZN5Caver27CharAnimControllerComponent8IsMovingEv",
	bool, (void* this)
);

static int c_stopmoving(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;

	StopMoving(c);
	return 0;
}

static int c_stopaction(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;

	StopAction(c);
	return 0;
}

static int c_startmoving(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;

	StartMoving(c);
	return 0;
}

static int c_begincasting(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;

	BeginCasting(c);
	return 0;
}

static int c_startfalling(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;

	StartFalling(c);
	return 0;
}

static int c_isready(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;
	bool isReady = IsReadyToJump(c);
	lua_pushboolean(L, isReady);
	return 1;
}

static int c_ismoving(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;
	bool isMoving = IsMoving(c);
	lua_pushboolean(L, isMoving);
	return 1;
}

static int c_nearlyfinished(lua_State *L) {
	SceneObject **o = lua_touserdata(L, 1);
	if (!o || !*o) return 0;

	void *c = SceneObject_ComponentWithInterface(
		*o, CharAnimController_Interface);

	if (!c) return 0;
	bool nearlyFinished = ActionNearlyFinished(c);
	lua_pushboolean(L, nearlyFinished);
	return 1;
}

static const luaL_Reg CharAnimController[] = {
	{"StopMoving", c_stopmoving},
	{"StopAction", c_stopaction},
	{"StartMoving", c_startmoving},
	{"BeginCasting", c_begincasting},
	{"StartFalling", c_startfalling},
	{"IsReadyToJump", c_isready},
	{"IsMoving", c_ismoving},
	{"ActionNearlyFinished", c_nearlyfinished},
	{NULL, NULL}
};

void API_register_charanimcontroller(lua_State *L) {
	lua_newtable(L);
	for (const luaL_Reg *lib = CharAnimController; lib->name; lib++) {
		lua_pushcfunction(L, lib->func);
		lua_setfield(L, -2, lib->name);
	}
	lua_setglobal(L, "CharAnimController");
}