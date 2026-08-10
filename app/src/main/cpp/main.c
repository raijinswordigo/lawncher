#include <jni.h>
#include "core/hook.h"
#include "core/core.h"
#include "lua/lua.h"
#include "lua/lauxlib.h"
#include "api/apis.h"

extern void init_lua();
extern void init_lual();

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_preload(JNIEnv *env, jclass clazz) {
	init_crasher();
	init_hooks();
	init_assets();
	init_lua();
	init_lual();
	initAPI_api();
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_init(JNIEnv *env, jclass clazz) {
}

