#include "controller.h"
#include "main.h"

static void setButtonsHidden(jboolean hidden) {
	GET_ENV(); GET_CLS();
	if (!env || !cls) return;
	jmethodID m = VOID_METHOD("setHiddenAll", "(Z)V");
	if (m) (*env)->CallStaticVoidMethod(env, cls, m, hidden);
}

static void hideAllButtons(void) { setButtonsHidden(JNI_TRUE); }
static void unhideAllButtons(void) { setButtonsHidden(JNI_FALSE); }

HOOK_SYMBOL(
	GameSceneView_SetCinematicMode,
	"_ZN5Caver13GameSceneView23SetCinematicModeEnabledEbbb",
	void, (void *thiz, bool enabled, bool animate, bool unknown)
) {
	if (enabled) hideAllButtons(); else unhideAllButtons();
	orig_GameSceneView_SetCinematicMode(thiz, enabled, animate, unknown);
}

HOOK_SYMBOL(
	GameMenu_LoadView,
	"_ZN5Caver22GameMenuViewController8LoadViewEv",
	void, (void *thiz)
) {
	hideAllButtons();
	orig_GameMenu_LoadView(thiz);
}

HOOK_SYMBOL(
	GameMenu_ViewWillDisappear,
	"_ZN5Caver22GameMenuViewController17ViewWillDisappearEv",
	void, (void *thiz)
) {
	unhideAllButtons();
	orig_GameMenu_ViewWillDisappear(thiz);
}

HOOK_SYMBOL(
	PauseView_C,
	"_ZN5Caver9PauseViewC2Ev",
	void, (void *thiz)
) {
	hideAllButtons();
	orig_PauseView_C(thiz);
}

HOOK_SYMBOL(
	PauseView_D,
	"_ZN5Caver9PauseView10TouchEndedERKNS_7FWTouchE",
	void, (void *thiz, void *touch)
) {
	unhideAllButtons();
	orig_PauseView_D(thiz, touch);
}

HOOK_SYMBOL(
	PortalView_AnimateIn,
	"_ZN5Caver10PortalView9AnimateInEv",
	void, (void *thiz)
) {
	hideAllButtons();
	orig_PortalView_AnimateIn(thiz);
}

HOOK_SYMBOL(
	PortalView_AnimateOut,
	"_ZN5Caver10PortalView10AnimateOutEv",
	void, (void *thiz)
) {
	unhideAllButtons();
	orig_PortalView_AnimateOut(thiz);
}

HOOK_SYMBOL(
	SkillPicker_Load,
	"_ZN5Caver25SkillPickerViewController8LoadViewEv",
	void, (void *thiz)
) {
	hideAllButtons();
	orig_SkillPicker_Load(thiz);
}

HOOK_SYMBOL(
	SkillPicker_Destroy,
	"_ZN5Caver25SkillPickerViewControllerD0Ev",
	void, (void *thiz)
) {
	unhideAllButtons();
	orig_SkillPicker_Destroy(thiz);
}

HOOK_SYMBOL(
	GameOverView_C,
	"_ZN5Caver12GameOverViewC2Ev",
	void, (void *thiz)
) {
	orig_GameOverView_C(thiz);
	button_remove_all();
}

void install_button_hooks(void) {
	// I'm sure the constructor attribute auto runs those hooks/dlsyms...
	LOGD("button cinematic hooks active");
}