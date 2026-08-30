#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"

#include <jni.h>
#include <string.h>

#define LOG_TAG "MusicHooks"

static jclass    g_mpClass      = NULL;
static jmethodID g_getMethod    = NULL;   // MusicPlayer.get()
static jmethodID g_loadFile     = NULL;
static jmethodID g_play         = NULL;
static jmethodID g_pause        = NULL;
static jmethodID g_stop         = NULL;
static jmethodID g_setLooping   = NULL;
static jmethodID g_setVolume    = NULL;

static int ensure_jni(JNIEnv *env) {
	if (g_mpClass) return 1;

	jclass local = (*env)->FindClass(env, "com/touchfoo/swordigo/MusicPlayer");
	if (!local) {
		LOGE("MusicPlayer class not found");
		(*env)->ExceptionClear(env);
		return 0;
	}
	g_mpClass = (jclass)(*env)->NewGlobalRef(env, local);
	(*env)->DeleteLocalRef(env, local);

	g_getMethod  = (*env)->GetStaticMethodID(env, g_mpClass, "get", "()Lcom/touchfoo/swordigo/MusicPlayer;");
	g_loadFile   = (*env)->GetMethodID(env, g_mpClass, "loadFile", "(Ljava/lang/String;)Z");
	g_play       = (*env)->GetMethodID(env, g_mpClass, "play", "()V");
	g_pause      = (*env)->GetMethodID(env, g_mpClass, "pause", "()V");
	g_stop       = (*env)->GetMethodID(env, g_mpClass, "stop", "()V");
	g_setLooping = (*env)->GetMethodID(env, g_mpClass, "setLooping", "(Z)V");
	g_setVolume  = (*env)->GetMethodID(env, g_mpClass, "setVolume", "(F)V");

	if (!g_getMethod || !g_loadFile || !g_play || !g_pause || !g_stop || !g_setLooping || !g_setVolume) {
		LOGE("missing MusicPlayer method(s)");
		(*env)->ExceptionClear(env);
		return 0;
	}
	return 1;
}

static jobject get_instance(JNIEnv *env) {
	if (!ensure_jni(env)) return NULL;
	jobject inst = (*env)->CallStaticObjectMethod(env, g_mpClass, g_getMethod);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return NULL;
	}
	return inst;
}

/* ---------- hooks – completely replace original MusicPlayerJNI ---------- */

HOOK_SYMBOL(
	LoadFile,
	"_ZN14MusicPlayerJNI8LoadFileERKSs",
	bool, (String **s)
) {
	const char *name = *s ? (const char *)*s : "";
	LOGD("LoadFile '%s'", name);

	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return false;

	jobject inst = get_instance(env);
	bool ok = false;
	if (inst) {
		jstring jname = (*env)->NewStringUTF(env, name);
		ok = (*env)->CallBooleanMethod(env, inst, g_loadFile, jname);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
			ok = false;
		}
		(*env)->DeleteLocalRef(env, jname);
		(*env)->DeleteLocalRef(env, inst);
	}

	java_release_env(attached);
	return ok;
}

HOOK_SYMBOL(
	Play,
	"_ZN14MusicPlayerJNI4PlayEv",
	void, (void)
) {
	LOGD("Play");
	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jobject inst = get_instance(env);
	if (inst) {
		(*env)->CallVoidMethod(env, inst, g_play);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		(*env)->DeleteLocalRef(env, inst);
	}
	java_release_env(attached);
}

HOOK_SYMBOL(
	Pause,
	"_ZN14MusicPlayerJNI5PauseEv",
	void, (void)
) {
	LOGD("Pause");
	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jobject inst = get_instance(env);
	if (inst) {
		(*env)->CallVoidMethod(env, inst, g_pause);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		(*env)->DeleteLocalRef(env, inst);
	}
	java_release_env(attached);
}

HOOK_SYMBOL(
	Stop,
	"_ZN14MusicPlayerJNI4StopEv",
	void, (void)
) {
	LOGD("Stop");
	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jobject inst = get_instance(env);
	if (inst) {
		(*env)->CallVoidMethod(env, inst, g_stop);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		(*env)->DeleteLocalRef(env, inst);
	}
	java_release_env(attached);
}

HOOK_SYMBOL(
	SetLooping,
	"_ZN14MusicPlayerJNI10SetLoopingEb",
	void, (bool loop)
) {
	LOGD("SetLooping %d", loop);
	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jobject inst = get_instance(env);
	if (inst) {
		(*env)->CallVoidMethod(env, inst, g_setLooping, (jboolean)loop);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		(*env)->DeleteLocalRef(env, inst);
	}
	java_release_env(attached);
}

HOOK_SYMBOL(
	SetVolume,
	"_ZN14MusicPlayerJNI9SetVolumeEf",
	void, (float vol)
) {
	LOGD("SetVolume %f", vol);
	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jobject inst = get_instance(env);
	if (inst) {
		(*env)->CallVoidMethod(env, inst, g_setVolume, vol);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		(*env)->DeleteLocalRef(env, inst);
	}
	java_release_env(attached);
}