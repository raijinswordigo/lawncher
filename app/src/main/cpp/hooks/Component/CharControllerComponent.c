#include "Component.h"
#include "Component/CharControllerComponent.h"
#include "lua.h"
#include "GameSceneController.h"

static CharControllerComponent *g_cc = NULL;

CharControllerComponent *charControllerComponent_get() {
	return g_cc;
}

CharControllerComponent *charControllerComponent_from_L(lua_State *L) {
	GameSceneController *gsc = gsc_from_L(L);
	SceneObject *hero = gsc->hero;
	void *cc = component_fetch(hero, "CharControllerComponent");
	return (CharControllerComponent *)cc;
}

HOOK_SYMBOL(
	Update,
	"_ZN5Caver23CharControllerComponent6UpdateEf",
	void, (CharControllerComponent *cc, float dt)
) {
	return orig_Update(cc, dt);
}