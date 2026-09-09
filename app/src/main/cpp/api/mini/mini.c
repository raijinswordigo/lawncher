#include "lua.h"
#include "hook.h"
#include "lauxlib.h"
#include "Scene.h"
#include "GameViewController.h"
#include "PlayerProfile.h"
#include "Component.h"
#include "log.h"

#define LOG_TAG "SwMiniLogs"
#define MINI_LIB_NAME "Mini"

static int Arch(lua_State *L) {
	lua_pushstring(L, archSplit("armeabi-v7a", "arm64-v8a"));
	return 1;
}

static int ToggleDebug(lua_State *L) {
	Scene *scene = scene_from_L(L);
	*$(bool, scene, 0x1c4, 0x2d0) = true;
	return 0;
}

DL_SYMBOL(
	GOV_SetControlsHidden,
	"_ZN5Caver15GameOverlayView17SetControlsHiddenEb",
	void, (void *gov, bool hidden)
); // why does this require a semicolon but G_DLs not?

static int SetControlsHidden(lua_State *L) {
	bool hidden = lua_toboolean(L, 1);
	GameViewController *gvc = gvc_from_L(L);
//	PlayerProfile *pp = (PlayerProfile*)gvc->PlayerProfile;
//	lua_pushstring(L, String_get(&pp->Identifier));
	/*
	 * pGVar11 = *(GameOverlayView **)(*(long *)(this + 0xd8) + 0x110);
	 * *(GameOverlayView **)(*(int *)(this + 0x70) + 0xd4);
	 */
	void *gsv = gvc->GameSceneView;
	void *gov = *$(void*, gsv, 0xd4, 0x110);
	*$(bool, gov, 0x11c, 0x198) = !hidden; // oh, the opposite
	*$(bool, gov, 0xc4, 0xf4) = hidden;
	GOV_SetControlsHidden(gov, hidden);
//	ToggleDebug(L);
	return 0;
}

static int GetProfileID(lua_State *L) {
	GameViewController *gvc = gvc_from_L(L);
	PlayerProfile *pp = (PlayerProfile*)gvc->PlayerProfile;
	if (!pp) return 0;
	lua_pushstring(L, String_get(&pp->Identifier)); // could be unsafe... I haven't looked at the implementation of String.
	return 1;
}

// Hmm, what other Mini functions are there?
// Oh yeah:
static int RecreateHero(lua_State *L) {
	GameSceneController *gsc = gsc_from_L(L);
	SceneObject *hero = gsc->hero;
//	Vector3 pos = {0.0f, 0.0f, 0.0f};
	Vector3 pos = hero->Position;
	Component *entity = component_fetch(hero, "EntityComponent");
//	Component *entity = SceneObject_ComponentWithInterface(hero, swordigo_dlsym("_ZN5Caver15EntityComponent9InterfaceEv"));
	if (!entity) {
		LOGE("RecreateHero was ran but SceneObject '%s' has no EntityComponent.");
		return 0;
	}
	int dir = *$(int, entity, 0x38, 0x68);
	GameSceneController_CreateHeroObjectAt(gsc, &pos, dir, true);
	return 0;
}

static const luaL_Reg lib[] = {
	{"Arch", Arch},
	{"ToggleDebug", ToggleDebug},
	{"SetControlsHidden", SetControlsHidden},
	{"GetProfileID", GetProfileID},
	{"RecreateHero", RecreateHero},
	{NULL, NULL}
};

extern void miniLL_register_character(lua_State *L);

void API_register_mini(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; lib[i].name; i++) {
		lua_pushcfunction(L, lib[i].func);
		lua_setfield(L, -2, lib[i].name);
	}
	miniLL_register_character(L);
	lua_setglobal(L, "Mini");
}