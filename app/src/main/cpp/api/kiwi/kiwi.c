#include "lua.h"
#include "lauxlib.h"
#include "hook.h"
#include "types.h"
#include "main/GameController.h"
#include "log.h"
#include "main/SceneObject.h"
#include "main/Scene.h"
#include "stdstring.h"
#include "components.h"
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define LOG_TAG "KiwiAPI"

DL_SYMBOL(
	GameSceneController_CreateHeroObjectAt,
	"_ZN5Caver19GameSceneController18CreateHeroObjectAtERKNS_7Vector3Eib",
	void, (GameSceneController *gsc, Vector3 *pos, int facing, bool sm)
);

DL_SYMBOL(
	GameOverlayView_SetControlsHidden,
	"_ZN5Caver15GameOverlayView17SetControlsHiddenEb",
	void, (void *gov, bool hidden)
);

extern int miniLL_test(lua_State *L);

static int miniLL_arch(lua_State *L) {
	lua_pushstring(L, archSplit("32", "64"));
	return 1;
}

static int miniLL_recreate_hero(lua_State *L) {
	GameSceneController *gsc = get_gsc();
	if (!gsc || !gsc->hero) return 0;
	Vector3 vec = gsc->hero->position;
	GameSceneController_CreateHeroObjectAt(gsc, &vec, 1, false);
	return 0;
}

static int miniLL_set_controls_hidden(lua_State *L) {
	bool hidden = lua_toboolean(L, 1);
	void *gov = get_gov();
	if (!gov) return 0;
	*$(bool*, gov, 0xBC, 0xE4) = &hidden;
	GameOverlayView_SetControlsHidden(gov, hidden);
	return 0;
}

static int miniLL_toggle_debug(lua_State *L) {
	bool toggle = lua_toboolean(L, 1);
	lua_getglobal(L, "scene");
	Scene *scene = (Scene *)lua_topointer(L, -1);
	lua_pop(L, 1);
	if (scene) scene->hitboxes = toggle;
	return 0;
}

static char *g_profile = NULL;

HOOK_SYMBOL(
	LoadGameState,
	"_ZN5Caver13PlayerProfile13LoadGameStateEv",
	void, (void *profile)
	) {
    const char *id = *$(String*, profile, 0xc, 0x18);
	if (g_profile) {
		free(g_profile);
		g_profile = NULL;
	}
	size_t len = strlen(id) + 1;
	g_profile = malloc(len);
	if (g_profile) memcpy(g_profile, id, len);
    return orig_LoadGameState(profile);
}

static int miniLL_get_profile(lua_State *L) {
	lua_pushstring(L, g_profile);
    return 1;
}

DL_SYMBOL(
	setModelName,
	"_ZN5Caver14ModelComponent12setModelNameERKSs",
	void, (void *mc, String **name)
	);

static int miniLL_set_model(lua_State *L) {
	SceneObject **obj = lua_touserdata(L, 1);
	const char *name = luaL_checkstring(L, 2);
	void *interface = swordigo_dlsym("_ZN5Caver14ModelComponent9InterfaceEv");
	void *comp = SceneObject_ComponentWithInterface(*obj, interface);
	String *mn;
	String_create(&mn, name);
	setModelName(comp, &mn);
	return 0;
}


DL_SYMBOL(
	SwingableWeapon__SetGlowColor,
"_ZN5Caver24SwingableWeaponComponent12SetGlowColorENS_10FloatColorE",
void, (void *this, void *color)
);

DL_SYMBOL(
	SwingableWeapon__SetGlowIntensity,
"_ZN5Caver24SwingableWeaponComponent16SetGlowIntensityEf",
void, (void *this, float intensity)
);

static int miniLL_set_weapon_color(lua_State *L) {
//	SceneObject **obj = luaL_checkudata(L, 1, "SceneObject");
//	float r = (float)lua_tonumber(L, 2);
//	float g = (float)lua_tonumber(L, 3);
//	float b = (float)lua_tonumber(L, 4);
//	float a = (float)lua_tonumber(L, 5);
//	float intensity = (float)lua_tonumber(L, 6);
//
//	void *SwingableWeaponControllerComponent = SceneObject_ComponentWithInterface(*obj, SwingableWeaponController_Interface);
//	if (!SwingableWeaponControllerComponent) return luaL_error(L, "Object does not have SwingableWeaponControllerComponent!");
//
//	void *SwingableWeaponComponent = *$(void*, SwingableWeaponControllerComponent, 0x50, 0x98);
//
//	FloatColor color = {r, g, b, a};
//	SwingableWeapon__SetGlowColor(SwingableWeaponComponent, &color);
//	SwingableWeapon__SetGlowIntensity(SwingableWeaponComponent, intensity);

	return 0;
}

static const luaL_Reg Mini[] = {
	{"Test",              miniLL_test},
	{"Arch",              miniLL_arch},
	{"RecreateHero",      miniLL_recreate_hero},
	{"SetControlsHidden", miniLL_set_controls_hidden},
	{"ToggleDebug",       miniLL_toggle_debug},
	{"GetProfileID",      miniLL_get_profile},
	{"SetModelName",      miniLL_set_model},

	{"SetTrinketColor",   miniLL_set_weapon_color},
	{NULL, NULL}
};

extern void API_mini_character(lua_State *L);
extern void API_register_charanimcontroller(lua_State *L);

void API_register_mini(lua_State *L) {
	lua_newtable(L);
	for (const luaL_Reg *lib = Mini; lib->name; lib++) {
		lua_pushcfunction(L, lib->func);
		lua_setfield(L, -2, lib->name);
	}
	API_mini_character(L);

	lua_setglobal(L, "Mini");

	API_register_charanimcontroller(L);
}

void initAPI_mini(void) {
}