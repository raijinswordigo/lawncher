#ifndef LAWNCHER_GAMEVIEWCONTROLLER_H
#define LAWNCHER_GAMEVIEWCONTROLLER_H

#include "hook.h"
#include "GameSceneController.h"
#include "GameState.h"
#include "lua.h"

typedef struct GameViewController {
	char _pad0[archSplit(0x48, 0x88)];
	void *PlayerProfile;
	char _pad1[archSplit(0x0c, 0x18)];
	GameState *GameState;
	char _pad2[archSplit(0x0c, 0x18)];
	GameSceneController *GameSceneController;
	char _pad3[archSplit(0x04, 0x08)];
	void *GameSceneView;
	char _pad4[archSplit(0x40, 0x50)];
} GameViewController;

GameViewController *gvc_from_L(lua_State *L);
GameViewController *gvc_get();

DL_SYMBOL_DECL(GameViewController_Update, void, (GameViewController *gvc, float dt));
DL_SYMBOL_DECL(GameViewController_LoadGameState, void, (GameViewController *gvc));
DL_SYMBOL_DECL(GameViewController_SaveGameState, void, (GameViewController *gvc, bool unknown));
DL_SYMBOL_DECL(GameViewController_GotoLevel, void, (GameViewController *gvc, String *level, String *spawn));
DL_SYMBOL_DECL(GameViewController_LoadView, void, (GameViewController *gvc));
DL_SYMBOL_DECL(GameViewController_ResetView, void, (GameViewController *gvc));
DL_SYMBOL_DECL(GameViewController_ResumeView, void, (GameViewController *gvc));
DL_SYMBOL_DECL(GameViewController_AddItemToCharacter, void, (GameViewController *gvc, void *shared_item));
DL_SYMBOL_DECL(GameViewController_RemoveItemFromCharacter, void, (GameViewController *gvc, void *shared_item));

#endif
