#include "lua.h"
#include "lauxlib.h"
#include "components.h"
#include "hook.h"
#include "main/SceneObject.h"
#include "main/GameController.h"

DL_SYMBOL(
	DropQuickly,
	"_ZN5Caver23CharControllerComponent11DropQuicklyEv",
	void, (void *cc)
	);

DL_SYMBOL(
	StopJumping,
	"_ZN5Caver23CharControllerComponent11StopJumpingEv",
	void, (void *cc)
	);

DL_SYMBOL(
	StartJumping,
	"_ZN5Caver23CharControllerComponent12StartJumpingEv",
	void, (void *cc)
	);

DL_SYMBOL(
	CancelCasting,
	"_ZN5Caver23CharControllerComponent13CancelCastingEv",
	void, (void *cc)
	);

DL_SYMBOL(
	FinishCasting,
	"_ZN5Caver23CharControllerComponent13FinishCastingEv",
	void, (void *cc)
	);

DL_SYMBOL(
	CanDoSomething,
	"_ZN5Caver23CharControllerComponent14CanDoSomethingEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	CanBeginCasting,
	"_ZN5Caver23CharControllerComponent15CanBeginCastingEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	StopMovingToDirection,
	"_ZN5Caver23CharControllerComponent21StopMovingToDirectionEi",
	void, (void *cc, int dir)
	);

DL_SYMBOL(
	StartMovingToDirection,
	"_ZN5Caver23CharControllerComponent22StartMovingToDirectionEi",
	void, (void *cc, int dir)
	);

DL_SYMBOL(
	Die,
	"_ZN5Caver23CharControllerComponent3DieEv",
	void, (void *cc)
	);

DL_SYMBOL(
	Use,
	"_ZN5Caver23CharControllerComponent3UseEv",
	void, (void *cc)
	);

DL_SYMBOL(
	Hurt,
	"_ZN5Caver23CharControllerComponent4HurtEv",
	void, (void *cc)
	);

DL_SYMBOL(
	Swing,
	"_ZN5Caver23CharControllerComponent5SwingEv",
	void, (void *cc)
	);

DL_SYMBOL(
	CanUse,
	"_ZN5Caver23CharControllerComponent6CanUseEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	CanJump,
	"_ZN5Caver23CharControllerComponent7CanJumpEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	CanSwing,
	"_ZN5Caver23CharControllerComponent8CanSwingEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	CanPickup,
	"_ZN5Caver23CharControllerComponent9CanPickupEv",
	bool, (void *cc)
	);

DL_SYMBOL(
	StopSwing,
	"_ZN5Caver23CharControllerComponent9StopSwingEv",
	void, (void *cc)
	);

// helper func, I should probably wrap the other functions in a macro.
static void *get_char_controller(void) {
	GameSceneController *gsc = get_gsc();
	if (!gsc) return NULL;
	void *hero = *$(void*, gsc, 0xa4, 0xd8);
	if (!hero) return NULL;
	return SceneObject_ComponentWithInterface(hero, CharController_Interface);
}

static int L_GetRunSpeed(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	lua_pushnumber(L, *$(float, cc, 0x178, 0x280));
	return 1;
}
static int L_SetRunSpeed(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	*$(float, cc, 0x178, 0x280) = (float)luaL_checknumber(L, 1);
	return 0;
}
static int L_GetWalkSpeed(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	lua_pushnumber(L, *$(float, cc, 0x170, 0x278));
	return 1;
}
static int L_SetWalkSpeed(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	*$(float, cc, 0x170, 0x278) = (float)luaL_checknumber(L, 1);
	return 0;
}
static int L_GetJumpHeight(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	lua_pushnumber(L, *$(float, cc, 0x164, 0x26c));
	return 1;
}
static int L_SetJumpHeight(lua_State *L) {
	void *cc = get_char_controller();
	if (!cc) return 0;
	*$(float, cc, 0x164, 0x26c) = (float)luaL_checknumber(L, 1);
	return 0;
}

static int L_DropQuickly(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) DropQuickly(cc);
	return 0;
}
static int L_StartJumping(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) StartJumping(cc);
	return 0;
}
static int L_StopJumping(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) StopJumping(cc);
	return 0;
}
static int L_CancelCasting(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) CancelCasting(cc);
	return 0;
}
static int L_FinishCasting(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) FinishCasting(cc);
	return 0;
}
static int L_Die(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) Die(cc);
	return 0;
}
static int L_Use(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) Use(cc);
	return 0;
}
static int L_Hurt(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) Hurt(cc);
	return 0;
}
static int L_Swing(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) Swing(cc);
	return 0;
}
static int L_StopSwing(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) StopSwing(cc);
	return 0;
}
static int L_CanDoSomething(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanDoSomething(cc));
	return 1;
}
static int L_CanBeginCasting(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanBeginCasting(cc));
	return 1;
}
static int L_CanUse(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanUse(cc));
	return 1;
}
static int L_CanJump(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanJump(cc));
	return 1;
}
static int L_CanSwing(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanSwing(cc));
	return 1;
}
static int L_CanPickup(lua_State *L) {
	void *cc = get_char_controller();
	lua_pushboolean(L, cc && CanPickup(cc));
	return 1;
}
static int L_StartMovingToDirection(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) StartMovingToDirection(cc, (int)luaL_checkinteger(L, 1));
	return 0;
}
static int L_StopMovingToDirection(lua_State *L) {
	void *cc = get_char_controller();
	if (cc) StopMovingToDirection(cc, (int)luaL_checkinteger(L, 1));
	return 0;
}

static int L_Test(lua_State *L) {
	lua_pushstring(L, "Mini.Character works");
	return 1;
}

static const luaL_Reg MiniCharacter[] = {
	{"GetRunSpeed", L_GetRunSpeed},
	{"SetRunSpeed", L_SetRunSpeed},
	{"GetWalkSpeed", L_GetWalkSpeed},
	{"SetWalkSpeed", L_SetWalkSpeed},
	{"GetJumpHeight", L_GetJumpHeight},
	{"SetJumpHeight", L_SetJumpHeight},
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
	{NULL, NULL}
};

void API_mini_character(lua_State *L) {
	lua_newtable(L);

	for (const luaL_Reg *lib = MiniCharacter; lib->name; lib++) {
		lua_pushcfunction(L, lib->func);
		lua_setfield(L, -2, lib->name);
	}

	lua_setfield(L, -2, "Character");
}