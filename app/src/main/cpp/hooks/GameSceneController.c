#include "GameSceneController.h"
#include "GameViewController.h"
#include "CharacterState.h"

static GameSceneController *g_gsc = NULL;
static CharacterState *g_characterState = NULL;

GameSceneController *gsc_from_L(lua_State *L) {
	GameViewController *gvc = gvc_from_L(L);
	return gvc->GameSceneController;
}

GameSceneController *gsc_get() {
	return g_gsc;
}

CharacterState *characterState_get() {
	return g_characterState;
}

HOOK_SYMBOL(
	Update,
	"_ZN5Caver19GameSceneController6UpdateEf",
	void, (GameSceneController *gsc, float dt)
) {
	g_gsc = gsc;
	if (gsc && gsc->CharacterState) g_characterState = (CharacterState *)gsc->CharacterState;
	return orig_Update(gsc, dt);
}

G_DL_SYMBOL(
	GameSceneController_Update,
	"_ZN5Caver19GameSceneController6UpdateEf",
	void, (GameSceneController *gsc, float dt)
);

G_DL_SYMBOL(
	GameSceneController_SpawnHeroAt,
	"_ZN5Caver19GameSceneController11SpawnHeroAtERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	void, (GameSceneController *gsc, String *id)
);

G_DL_SYMBOL(
	GameSceneController_UpdateTarget,
	"_ZN5Caver19GameSceneController12UpdateTargetEv",
	void, (GameSceneController *gsc)
);

G_DL_SYMBOL(
	GameSceneController_EquipItem,
	"_ZN5Caver19GameSceneController9EquipItemERKN5boost10shared_ptrINS_4ItemEEE",
	void, (GameSceneController *gsc, void *shared_item)
);

G_DL_SYMBOL(
	GameSceneController_UnequipArmor,
	"_ZN5Caver19GameSceneController12UnequipArmorEv",
	void, (GameSceneController *gsc)
);

G_DL_SYMBOL(
	GameSceneController_BeginCasting,
	"_ZN5Caver19GameSceneController12BeginCastingERKN5boost10shared_ptrINS_5SkillEEE",
	void, (GameSceneController *gsc, void *shared_skill)
);

G_DL_SYMBOL(
	GameSceneController_CanCastSkill,
	"_ZN5Caver19GameSceneController12CanCastSkillERKN5boost10shared_ptrINS_5SkillEEE",
	bool, (GameSceneController *gsc, void *shared_skill)
);

G_DL_SYMBOL(
	GameSceneController_ConsumeItem,
	"_ZN5Caver19GameSceneController11ConsumeItemERKN5boost10shared_ptrINS_4ItemEEE",
	void, (GameSceneController *gsc, void *shared_item)
);

G_DL_SYMBOL(
	GameSceneController_ApplyLevelUp,
	"_ZN5Caver19GameSceneController12ApplyLevelUpEiii",
	void, (GameSceneController *gsc, int a, int b, int c)
);

G_DL_SYMBOL(
	CharacterState_ExperiencePointsRequiredForLevel,
	"_ZN5Caver14CharacterState32ExperiencePointsRequiredForLevelEi",
	int, (CharacterState *cs, int level)
);
