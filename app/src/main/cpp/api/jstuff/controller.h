#ifndef LAWNCHER_API_JSTUFF_CONTROLLER_H
#define LAWNCHER_API_JSTUFF_CONTROLLER_H

#include "java.h"
#include "hook.h"
#include "lauxlib.h"
#include "lua.h"

#include <jni.h>
#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>


#define LOG_TAG "JStuff"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define BUTTON_MT    "MiniButton"
#define OVERLAY_MT   "MiniOverlay"
#define DRAWER_MT    "MiniDrawer"
#define SEEKBAR_MT   "MiniSeekBar"
#define TEXTINPUT_MT "MiniTextInput"

#define JCLASS "net/kiwi/lawncher/ButtonController"

typedef struct {
	char *id;
	char *label;
	float nx, ny, nw, nh;
	int deleted;
	int alwaysActive;
} MiniButton;

typedef struct {
	char *id;
	float nx, ny, nw, nh;
	int alwaysActive;
} MiniOverlay;

typedef struct {
	char *id;
	float nx, ny, nw, nh;
	int alwaysActive;
} MiniDrawer;

typedef struct {
	char *id;
	int alwaysActive;
} MiniSeekBar;

typedef struct {
	char *id;
	float nx, ny, nw, nh;
	int alwaysActive;
} MiniTextInput;

extern jclass g_btn_cls;

#define CHECK_BTN(L)       ((MiniButton*)luaL_checkudata(L, 1, BUTTON_MT))
#define CHECK_OVERLAY(L)   ((MiniOverlay*)luaL_checkudata(L, 1, OVERLAY_MT))
#define CHECK_DRAWER(L)    ((MiniDrawer*)luaL_checkudata(L, 1, DRAWER_MT))
#define CHECK_SEEKBAR(L)   ((MiniSeekBar*)luaL_checkudata(L, 1, SEEKBAR_MT))
#define CHECK_TEXTINPUT(L) ((MiniTextInput*)luaL_checkudata(L, 1, TEXTINPUT_MT))

static inline JNIEnv *j_env(void) {
	int attached = 0;
	return java_get_env(&attached);
}

#define GET_ENV() JNIEnv *env = j_env()
#define GET_CLS() jclass cls = g_btn_cls

#define JID(env, o) (*env)->NewStringUTF(env, (o)->id)

#define VOID_METHOD(name, sig) (*env)->GetStaticMethodID(env, cls, name, sig)
#define INT_METHOD(name, sig)  (*env)->GetStaticMethodID(env, cls, name, sig)
#define BOOL_METHOD(name, sig) (*env)->GetStaticMethodID(env, cls, name, sig)

#define CALL_VOID_ID(mname, sig, ...) do { \
	GET_ENV(); GET_CLS(); \
	jmethodID m = VOID_METHOD(mname, sig); \
	jstring jid = JID(env, btn); \
	(*env)->CallStaticVoidMethod(env, cls, m, jid, ##__VA_ARGS__); \
	(*env)->DeleteLocalRef(env, jid); \
} while (0)

#define CALL_BOOL_ID(mname, sig, result) do { \
	GET_ENV(); GET_CLS(); \
	jmethodID m = BOOL_METHOD(mname, sig); \
	jstring jid = JID(env, btn); \
	result = (*env)->CallStaticBooleanMethod(env, cls, m, jid); \
	(*env)->DeleteLocalRef(env, jid); \
} while (0)

int mini_newindex(lua_State *L);

int NewButton(lua_State *L);
int NewOverlay(lua_State *L);
int NewDrawer(lua_State *L);
int NewSeekBar(lua_State *L);
int NewTextInput(lua_State *L);

void register_legacy_bc(lua_State *L);

void open_button_mt(lua_State *L);
void open_overlay_mt(lua_State *L);
void open_drawer_mt(lua_State *L);
void open_seekbar_mt(lua_State *L);
void open_textinput_mt(lua_State *L);

void install_button_hooks(void);

#endif
