#ifndef LAWNCHER_GAMESCENECONTROLLER_H
#define LAWNCHER_GAMESCENECONTROLLER_H

#include "hook.h"
#include "SceneObject.h"
#include "CharacterState.h"
#include "lua.h"

typedef struct GameSceneController {
	void *vtable;
	char _pad0[archSplit(0x04, 0x08)];
	CharacterState *CharacterState;
	char _pad1[archSplit(0x08, 0x10)];
	void *Scene;
	char _pad2[archSplit(0x90, 0xb0)];
	SceneObject *hero;
	void *CharControllerComponent;
	void *EntityComponent;
	void *HealthComponent;
	void *ManaComponent;
	void *HeroEquipmentManager;
	char _pad3[archSplit(0x24, 0x44)];
	Vector3 spawnPosition;
	char _pad4[archSplit(0x20, 0x30)];
} GameSceneController;

GameSceneController *gsc_from_L(lua_State *L);
GameSceneController *gsc_get();

DL_SYMBOL_DECL(GameSceneController_Update, void, (GameSceneController *gsc, float dt));
DL_SYMBOL_DECL(GameSceneController_SpawnHeroAt, void, (GameSceneController *gsc, String *id));
DL_SYMBOL_DECL(GameSceneController_UpdateTarget, void, (GameSceneController *gsc));
DL_SYMBOL_DECL(GameSceneController_EquipItem, void, (GameSceneController *gsc, void *shared_item));
DL_SYMBOL_DECL(GameSceneController_UnequipArmor, void, (GameSceneController *gsc));
DL_SYMBOL_DECL(GameSceneController_BeginCasting, void, (GameSceneController *gsc, void *shared_skill));
DL_SYMBOL_DECL(GameSceneController_CanCastSkill, bool, (GameSceneController *gsc, void *shared_skill));
DL_SYMBOL_DECL(GameSceneController_ConsumeItem, void, (GameSceneController *gsc, void *shared_item));
DL_SYMBOL_DECL(GameSceneController_ApplyLevelUp, void, (GameSceneController *gsc, int a, int b, int c));

#endif
