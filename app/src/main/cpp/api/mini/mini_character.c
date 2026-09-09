#include "lua.h"
#include "hook.h"
#include "lauxlib.h"
#include "Scene.h"
#include "GameViewController.h"
#include "Component.h"
#include "Component/CharControllerComponent.h"

static CharControllerComponent* fetch(lua_State *L) {
	GameSceneController *gsc = gsc_from_L(L);
	SceneObject *hero = gsc->hero;
	return (CharControllerComponent*)component_fetch(hero, "CharControllerComponent");
}

static int GetWalkSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	lua_pushnumber(L, ctrl->runSpeed);
	return 1;
}

static int SetWalkSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	ctrl->runSpeed = (float)lua_tonumber(L, 1);
	return 0;
}

static int GetRunSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	lua_pushnumber(L, ctrl->fastRunSpeed);
	return 1;
}

static int SetRunSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	ctrl->fastRunSpeed = (float)lua_tonumber(L, 1);
	return 0;
}

static int GetJumpSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	lua_pushnumber(L, ctrl->jumpSpeed);
	return 1;
}

static int SetJumpSpeed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	ctrl->jumpSpeed = (float)lua_tonumber(L, 1);
	return 0;
}

static int GetAirJumpUsed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	lua_pushinteger(L, ctrl->airJumpCount);
	return 1;
}

static int SetAirJumpUsed(lua_State *L) {
	CharControllerComponent *ctrl = fetch(L);
	if (!ctrl) return 0;
	ctrl->airJumpCount = (int)lua_tointeger(L, 1);
	return 0;
}

static const luaL_Reg attribute_functions[] = {
	{"GetWalkSpeed", GetWalkSpeed},
	{"SetWalkSpeed", SetWalkSpeed},
	{"GetRunSpeed", GetRunSpeed},
	{"SetRunSpeed", SetRunSpeed},
	{"GetJumpSpeed", GetJumpSpeed},
	{"SetJumpSpeed", SetJumpSpeed},
	{"GetAirJumpUsed", GetAirJumpUsed},
	{"SetAirJumpUsed", SetAirJumpUsed},
	{NULL, NULL}
};

// Caver functions, just like the ones in SwKiwi!

#define VOID_FUNC(NAME, SYM) \
DL_SYMBOL( \
	NAME, \
	SYM, \
	void, (void *cc) \
); \
static int L_##NAME(lua_State *L) { \
	void *component = fetch(L); \
	NAME(component); \
	return 0; \
}

#define BOOL_FUNC(NAME, SYM) \
DL_SYMBOL( \
	NAME, \
	SYM, \
	bool, (void *cc) \
); \
static int L_##NAME(lua_State *L) { \
	void *component = fetch(L); \
	bool b = NAME(component); \
	lua_pushboolean(L, b); \
	return 1; \
}

#define DIR_FUNC(NAME, SYM) \
DL_SYMBOL( \
	NAME, \
	SYM, \
	void, (void *cc, int dir) \
); \
static int L_##NAME(lua_State *L) { \
	int dir = luaL_checkinteger(L, 1); \
	void *component = fetch(L); \
	NAME(component, dir == 1 ? 1 : -1); \
	return 0; \
}

VOID_FUNC(DropQuickly, "_ZN5Caver23CharControllerComponent11DropQuicklyEv")
VOID_FUNC(StartJumping, "_ZN5Caver23CharControllerComponent12StartJumpingEv")
VOID_FUNC(StopJumping, "_ZN5Caver23CharControllerComponent11StopJumpingEv")
VOID_FUNC(CancelCasting, "_ZN5Caver23CharControllerComponent13CancelCastingEv")
VOID_FUNC(FinishCasting, "_ZN5Caver23CharControllerComponent13FinishCastingEv")
VOID_FUNC(Die, "_ZN5Caver23CharControllerComponent3DieEv")
VOID_FUNC(Use, "_ZN5Caver23CharControllerComponent3UseEv")
VOID_FUNC(Hurt, "_ZN5Caver23CharControllerComponent4HurtEv")
VOID_FUNC(Swing, "_ZN5Caver23CharControllerComponent5SwingEv")
VOID_FUNC(StopSwing, "_ZN5Caver23CharControllerComponent9StopSwingEv")

BOOL_FUNC(CanDoSomething, "_ZN5Caver23CharControllerComponent14CanDoSomethingEv")
BOOL_FUNC(CanBeginCasting, "_ZN5Caver23CharControllerComponent15CanBeginCastingEv")
BOOL_FUNC(CanUse, "_ZN5Caver23CharControllerComponent6CanUseEv")
BOOL_FUNC(CanJump, "_ZN5Caver23CharControllerComponent7CanJumpEv")
BOOL_FUNC(CanSwing, "_ZN5Caver23CharControllerComponent8CanSwingEv")
BOOL_FUNC(CanPickup, "_ZN5Caver23CharControllerComponent9CanPickupEv")

DIR_FUNC(StartMovingToDirection, "_ZN5Caver23CharControllerComponent22StartMovingToDirectionEi")
DIR_FUNC(StopMovingToDirection, "_ZN5Caver23CharControllerComponent21StopMovingToDirectionEi")

static int L_SetMovementFacingLock(lua_State *L) {
	bool lock = lua_toboolean(L, 1);
	CharControllerComponent *cc = fetch(L);
	*$(bool, cc, 0x1c4, 0x2e4) = lock;
	return 0;
}

static int L_SetStunTime(lua_State *L) {
	float time = (float)lua_tonumber(L, 1);
	CharControllerComponent *cc = fetch(L);
	*$(float, cc, 0x11c, 0x220) = time;
	return 0;
}

static const luaL_Reg caver_functions[] = {
	{"DropQuickly", L_DropQuickly},
	{"StartJumping", L_StartJumping},
	{"StopJumping", L_StopJumping},
	{"CancelCasting", L_CancelCasting},
	{"FinishCasting", L_FinishCasting},
	{"Die", L_Die},
	{"Use", L_Use},
	{"Hurt", L_Hurt},
	{"Swing", L_Swing},
	{"StopSwing", L_StopSwing},

	{"CanDoSomething", L_CanDoSomething},
	{"CanBeginCasting", L_CanBeginCasting},
	{"CanUse", L_CanUse},
	{"CanJump", L_CanJump},
	{"CanSwing", L_CanSwing},
	{"CanPickup", L_CanPickup},

	{"StartMovingToDirection", L_StartMovingToDirection},
	{"StopMovingToDirection", L_StopMovingToDirection},
	{"SetMovementFacingLock", L_SetMovementFacingLock},

	{"SetStunTime", L_SetStunTime},
	{NULL, NULL}
};

void miniLL_register_character(lua_State *L) {
	lua_newtable(L);

	for (int i = 0; attribute_functions[i].name; i++) {
		lua_pushcfunction(L, attribute_functions[i].func);
		lua_setfield(L, -2, attribute_functions[i].name);
	}
	for (int i = 0; caver_functions[i].name; i++) {
		lua_pushcfunction(L, caver_functions[i].func);
		lua_setfield(L, -2, caver_functions[i].name);
	}

	lua_setfield(L, -2, "Character");
}