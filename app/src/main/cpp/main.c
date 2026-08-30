#include <jni.h>
#include <string.h>
#include "core/hook.h"
#include "core/core.h"
#include "lua/lua.h"
#include "lua/lauxlib.h"
#include "api/apis.h"
#include "stdstring.h"
#include "components.h"
#include "log.h"

#define LOG_TAG "LawncherMain"

extern void init_lua(void);
extern void init_lual(void);
extern void initC_caver(void);
//extern void init_assets(void);
extern void assets_on_mod_exit(void);
extern void cstrings_on_mod_exit(void);
extern void initAPI_cstrings(void);

//extern void init_jpatch(void);

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_loadHooks(JNIEnv *env, jclass clazz) {
	init_crasher();
	init_hooks();
	init_stdstring();
	initC_caver();
	init_components();
	init_assets();
	init_lua();
	init_lual();
	initAPI_api();
	initAPI_cstrings();
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_onModExit(JNIEnv *env, jclass clazz) {
	assets_on_mod_exit();
	cstrings_on_mod_exit();
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_init(JNIEnv *env, jclass clazz) {
}

HOOK_SYMBOL(
	AndroidIsGoogleGameServicesAvailable,
	"_ZN5Caver36AndroidIsGoogleGameServicesAvailableEv",
	bool, (void)
) {
	LOGD("yoyoyo");
	return false;
}