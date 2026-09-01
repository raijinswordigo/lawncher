#include "java.h"
#include "hook.h"
#include "stdstring.h"
#include "log.h"

#include <jni.h>
#include <string.h>

#define LOG_TAG "MusicHooks"

HOOK_SYMBOL(
	LoadFile,
	"_ZN14MusicPlayerJNI8LoadFileERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE",
	bool, (String *s)
) {
	LOGD("%s", String_get(s));
	return orig_LoadFile(s);
}