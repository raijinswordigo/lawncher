#ifndef LAWNCHER_GAMECONTROLLER_H
#define LAWNCHER_GAMECONTROLLER_H

#include "SceneObject.h"
#include "Scene.h"

typedef struct GameViewController {
	void *vtable;
	char _pad0[archSplit(0x20, 0x40)];
	void *HandleGameEvent_vtable;
	void *GameControlButtonDown_vtable;
	void *GameMenuViewControllerDidQuitToMenu_vtable;
	void *unknownDelegate_vtable;
	void *PortalViewControllerDidGotoLevel_vtable;
	void *ProfileManagerDelegate_vtable;
	void *StoreViewControllerDismissed_vtable;
	char _pad1[8];
	void *playerProfile;
	void *playerProfileRefCount;
	void *gameData;
	void *gameDataRefCount;
	void *gameState;
	void *gameStateRefCount;
	void *scene;
	void *sceneRefCount;
	void *gameSceneController;
	void *gameSceneControllerRefCount;
	void *gameSceneView;
	void *gameSceneViewRefCount;
	char isLoaded;
	char backgroundLoadComplete;
	char _pad2[archSplit(0x2, 0x6)];
	void *currentMusicName;
	char profileDownloadPending;
	char _pad3[archSplit(0x3, 0x7)];
	void *downloadedProfile;
	void *downloadedProfileRefCount;
	char gameCompletedPendingCredits;
	char _pad4[archSplit(0x3, 0x7)];
	void *guideTarget;
	void *guideTargetRefCount;
	void *guideArrowSceneObject;
	float guideTargetTimer;
	char _pad5[archSplit(0x0, 0x4)];
	void *portalTag;
} GameViewController;

typedef struct GameMenuViewController {
	void *vtable;
	char _pad0[archSplit(0x20, 0x40)];
	void *InventoryViewSelectedItemDidChange_vtable;
	void *CharacterViewDismissed_vtable;
	void *TabbedMenuViewSelectedTabDidChange_vtable;
	void *SettingsViewWantsToConfigureControls_vtable;
	void *GuideToggleViewEnableStateChanged_vtable;
	void *PurchaseViewControllerDidPurchaseProduct_vtable;
	void *StoreViewControllerDismissed_vtable;
	void *delegate;
	void *gameState;
	void *gameStateRefCount;
	void *contentView;
	void *contentViewRefCount;
	void *compassGuideToggleView;
	void *compassGuideToggleViewRefCount;
	void *coinDoublerGuideToggleView;
	void *coinDoublerGuideToggleViewRefCount;
	char _pad1[archSplit(0x4, 0x8)];
} GameMenuViewController;

typedef struct GameSceneController {
	void *vtable;
	void *characterState;
	void *characterStateRefCount;
	void *gameState;
	Scene *scene;
	void *sceneRefCount;
	void *gameSceneView;
	char _pad0[archSplit(0x88, 0xa0)];
	SceneObject *hero;
	void *charController; // CharControllerComponent*
	void *heroEntity; // EntityComponent*
	void *healthComponent; // HealthComponent*
	void *manaComponent; // ManaComponent*
	char _pad1[archSplit(0x24, 0x48)];
	float facingInterp;
	Vector3 heroSpawnPos;
	void *currentCastSkill; // Skill*
	void *currentCastSkillRefCount;
	float castTimeRemaining;
	SceneObject *target;
	bool fallTriggered;
	float levelUpCheckTimer;
	bool paused;
	uint32_t unk184;
} GameSceneController;

// Getter definitions
GameViewController *get_gvc(void);
GameSceneController *get_gsc(void);
GameViewController *get_gov(void);

#endif //LAWNCHER_GAMECONTROLLER_H