#ifndef LAWNCHER_CORE_JAVA_H
#define LAWNCHER_CORE_JAVA_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JavaVM *java_get_vm(void);
JNIEnv *java_get_env(int *out_attached);
void java_release_env(int attached);

const char *java_internal_files(void);
const char *java_external_files(void);
const char *java_current_mod_id(void);

/* call when leaving a mod session so next launch re-reads currentMod */
void java_reset_mod_id(void);

void java_set_main_activity(jclass clazz, jmethodID current_mod_method);

#ifdef __cplusplus
}
#endif

#endif