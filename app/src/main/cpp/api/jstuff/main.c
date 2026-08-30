#include "main.h"
#include "controller.h"

jclass g_btn_cls = NULL;

int mini_newindex(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_pushvalue(L, 3);
	lua_settable(L, -3);
	return 0;
}

static int removeAll(lua_State *L) {
	(void)L;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeAll", "()V");
	if (m) (*env)->CallStaticVoidMethod(env, cls, m);
	return 0;
}

static int setHiddenAll(lua_State *L) {
	jboolean hidden = (jboolean)lua_toboolean(L, 1);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setHiddenAll", "(Z)V");
	if (m) (*env)->CallStaticVoidMethod(env, cls, m, hidden);
	return 0;
}

void button_remove_all(void) {
	GET_ENV(); GET_CLS();
	if (!env || !cls) return;
	jmethodID m = VOID_METHOD("removeAll", "()V");
	if (m) (*env)->CallStaticVoidMethod(env, cls, m);
}

static const luaL_Reg OverlayController[] = {
	{"NewButton",    NewButton},
	{"NewOverlay",   NewOverlay},
	{"NewDrawer",    NewDrawer},
	{"NewSeekBar",   NewSeekBar},
	{"NewTextInput", NewTextInput},
	{"RemoveAll",    removeAll},
	{"HideAll",      setHiddenAll},
	{NULL, NULL}
};

void API_register_java_stuff(lua_State *L) {
	open_button_mt(L);
	open_overlay_mt(L);
	open_drawer_mt(L);
	open_seekbar_mt(L);
	open_textinput_mt(L);

	lua_newtable(L);
	for (int i = 0; OverlayController[i].name; i++) {
		lua_pushcfunction(L, OverlayController[i].func);
		lua_setfield(L, -2, OverlayController[i].name);
	}
	lua_setglobal(L, "OverlayController");
	register_legacy_bc(L);
}

void initAPI_java(void) {
	GET_ENV();
	if (!env) {
		LOGE("initAPI_java: no JNIEnv");
		return;
	}

	jclass local = (*env)->FindClass(env, JCLASS);
	if (!local) {
		LOGE("initAPI_java: class not found %s", JCLASS);
		(*env)->ExceptionClear(env);
		return;
	}

	g_btn_cls = (jclass)(*env)->NewGlobalRef(env, local);
	(*env)->DeleteLocalRef(env, local);

	install_button_hooks();
	LOGD("jstuff OverlayController ready");
}