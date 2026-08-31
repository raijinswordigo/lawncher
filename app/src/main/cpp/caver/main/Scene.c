#include "caver.h"
#include "GameController.h"
#include "Scene.h"
#include "log.h"

#define LOG_TAG "LawncherScene"

static Scene *g_scene = NULL;

Scene *get_scene() {
	return g_scene;
}

HOOK_SYMBOL(
	Scene_Update,
	"_ZN5Caver5Scene6UpdateEf",
	void, (Scene *scene, float dt)
	) {
	g_scene = scene;
	return orig_Scene_Update(scene, dt);
}

HOOK_SYMBOL(
	Scene_CTor,
	"_ZN5Caver5SceneC1Ev",
	void, (Scene *scene)
	) {
//	gsc_setSpeed(0.5f);
	return orig_Scene_CTor(scene);
}

HOOK_SYMBOL(
	Scene_DTor,
	"_ZN5Caver5SceneD0Ev",
	void, (Scene *scene)
	) {
	gsc_setSpeed(1.0f);
	// this func is also hooked in events/scene.c but i gotta rm events soon
	return orig_Scene_DTor(scene);
}

void initC_scene() {
	LOGD("Scene hooks placed (I think)");
}