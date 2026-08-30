#include "hook.h"
#include "GameController.h"

static GameSceneController *gsc_instance = NULL;
static GameViewController *gvc_instance = NULL;
static void *gov_instance = NULL;

GameSceneController *get_gsc(void) {
	return gsc_instance;
}

GameViewController *get_gvc(void) {
	return gvc_instance;
}

GameViewController *get_gov(void) {
	return gov_instance;
}

HOOK_SYMBOL(
	GameSceneController_Update,
	"_ZN5Caver19GameSceneController6UpdateEf",
	void, (GameSceneController *gsc, float dt)
) {
	gsc_instance = gsc;
	return orig_GameSceneController_Update(gsc, dt);
}

HOOK_SYMBOL(
	GameViewController_Update,
	"_ZN5Caver18GameViewController6UpdateEf",
	void, (GameViewController *gvc, float dt)
) {
	gvc_instance = gvc;
	return orig_GameViewController_Update(gvc, dt);
}

HOOK_SYMBOL(
	GameOverlayView_Constructor,
	"_ZN5Caver15GameOverlayViewC2Ev",
	void, (void *gov)
	) {
	gov_instance = gov;
	return orig_GameOverlayView_Constructor(gov);
}