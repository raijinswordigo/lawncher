#include "hook.h"
#include "GameController.h"

static GameSceneController *gsc_instance = NULL;
static GameViewController *gvc_instance = NULL;
static float g_speed = 1.0f;
static float g_dt = 1.0f / 60.0f;
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

void gsc_setSpeed(float speed) {
	g_speed = speed;
}

float gsc_getDt() {
	return g_dt;
}

float gsc_getSpeed() {
	return g_speed;
}

HOOK_SYMBOL(
	GameSceneController_Update,
	"_ZN5Caver19GameSceneController6UpdateEf",
	void, (GameSceneController *gsc, float dt)
) {
	gsc_instance = gsc;
	g_dt = dt * g_speed;
	return orig_GameSceneController_Update(gsc, dt * g_speed);
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