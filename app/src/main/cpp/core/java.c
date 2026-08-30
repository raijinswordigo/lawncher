#include "java.h"

#include <stdio.h>
#include <string.h>
#include "log.h"

#define LOG_TAG "LawncherJava"

static JavaVM *g_vm = NULL;
static jclass g_mainActivityClass = NULL;
static jmethodID g_currentModMethod = NULL;

static char g_internalfiles[256] = {0};
static char g_externalfiles[256] = {0};

static char g_current_mod[128] = {0};
static int g_mod_fetched = 0;

JavaVM *java_get_vm(void) {
	return g_vm;
}

JNIEnv *java_get_env(int *out_attached) {
	if (out_attached) *out_attached = 0;
	if (!g_vm) return NULL;

	JNIEnv *env = NULL;
	int status = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);

	if (status == JNI_EDETACHED) {
		if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
			LOGE("AttachCurrentThread failed");
			return NULL;
		}
		if (out_attached) *out_attached = 1;
	} else if (status != JNI_OK) {
		LOGE("GetEnv failed (%d)", status);
		return NULL;
	}
	return env;
}

void java_release_env(int attached) {
	if (attached && g_vm)
		(*g_vm)->DetachCurrentThread(g_vm);
}

const char *java_internal_files(void) {
	return g_internalfiles;
}

const char *java_external_files(void) {
	return g_externalfiles;
}

static void fetch_current_mod(void) {
	if (!g_vm || !g_mainActivityClass || !g_currentModMethod) {
		LOGE("JNI not ready, treating as vanilla");
		return;
	}

	int attached = 0;
	JNIEnv *env = java_get_env(&attached);
	if (!env) return;

	jstring jmod = (jstring)(*env)->CallStaticObjectMethod(
		env, g_mainActivityClass, g_currentModMethod);

	if (jmod) {
		const char *chars = (*env)->GetStringUTFChars(env, jmod, NULL);
		if (chars) {
			snprintf(g_current_mod, sizeof(g_current_mod), "%s", chars);
			(*env)->ReleaseStringUTFChars(env, jmod, chars);
		}
		(*env)->DeleteLocalRef(env, jmod);
	} else {
		g_current_mod[0] = '\0';
	}

	java_release_env(attached);

	LOGI("current mod: '%s'", g_current_mod[0] ? g_current_mod : "(vanilla)");
}

const char *java_current_mod_id(void) {
	if (!g_mod_fetched) {
		fetch_current_mod();
		g_mod_fetched = 1;
	}
	return g_current_mod;
}

void java_reset_mod_id(void) {
	g_current_mod[0] = '\0';
	g_mod_fetched = 0;
}

void java_set_main_activity(jclass clazz, jmethodID current_mod_method) {
	g_mainActivityClass = clazz;
	g_currentModMethod = current_mod_method;
}

JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_MainActivity_initPaths(JNIEnv *env, jclass clazz,
                                              jstring internalFiles, jstring externalFiles)
{
	if (internalFiles) {
		const char *p = (*env)->GetStringUTFChars(env, internalFiles, NULL);
		if (p) {
			snprintf(g_internalfiles, sizeof(g_internalfiles), "%s", p);
			(*env)->ReleaseStringUTFChars(env, internalFiles, p);
		}
	}

	if (externalFiles) {
		const char *p = (*env)->GetStringUTFChars(env, externalFiles, NULL);
		if (p) {
			snprintf(g_externalfiles, sizeof(g_externalfiles), "%s", p);
			(*env)->ReleaseStringUTFChars(env, externalFiles, p);
		}
	}

	(*env)->GetJavaVM(env, &g_vm);

	jclass global = (jclass)(*env)->NewGlobalRef(env, clazz);
	jmethodID method = (*env)->GetStaticMethodID(
		env, global, "currentMod", "()Ljava/lang/String;");

	if (!method) {
		LOGE("MainActivity.currentMod() not found");
		(*env)->ExceptionClear(env);
	}

	java_set_main_activity(global, method);

	LOGI("paths: internal=%s external=%s", g_internalfiles, g_externalfiles);
}