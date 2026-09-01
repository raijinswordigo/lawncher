#include "hook.h"
#include "java.h"
#include "core.h"
#include "log.h"
#include "stdstring.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <jni.h>

#define LOG_TAG "LawncherAssets"

static const char *path_basename(const char *path) {
	const char *slash = strrchr(path, '/');
	return slash ? slash + 1 : path;
}

static void ensure_dir(const char *path) {
	char buf[512];
	snprintf(buf, sizeof(buf), "%s", path);
	for (char *p = buf + 1; *p; p++) {
		if (*p == '/') {
			*p = '\0';
			mkdir(buf, 0770);
			*p = '/';
		}
	}
	mkdir(buf, 0770);
}

static int copy_file(const char *src, const char *dst) {
	FILE *in = fopen(src, "rb");
	if (!in) return 0;

	FILE *out = fopen(dst, "wb");
	if (!out) {
		fclose(in);
		return 0;
	}

	char buf[8192];
	size_t n;
	int ok = 1;
	while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
		if (fwrite(buf, 1, n, out) != n) {
			ok = 0;
			break;
		}
	}

	fclose(in);
	fclose(out);
	if (!ok) remove(dst);
	return ok;
}

HOOK_SYMBOL(
	NewByteBufferFromAA,
	"_ZN5Caver29NewByteBufferFromAndroidAssetERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEEPj",
	void*, (String *file, uint *param_2)
	) {
	const char *respath = java_resource_path(String_get(file));
	FILE *f = fopen(respath, "rb");
	if (!f) goto bailout;

//	fclose(f);
//	String s;
//	String_create(&s, respath);
//	return orig_NewByteBufferFromAA(&s, param_2);
	fseek(f, 0, SEEK_END);
	size_t size = ftell(f);
	fseek(f, 0, SEEK_SET);
	if (size < 0) goto bailout;
	void *buf = malloc(size);
	if (!buf) goto bailout;
	size_t readbytes = fread(buf, 1, size, f);
	fclose(f); // ty kizi for the warning
	*param_2 = (uint)readbytes; // param2 is outbytes
	LOGD("Modded Asset %s", respath);
	return buf;

	bailout:
	if (f) fclose(f);
	return orig_NewByteBufferFromAA(file, param_2);
}

/* called from Java when we leave a mod / return to launcher */
void assets_on_mod_exit(void) {
	java_reset_mod_id();
	LOGI("assets_on_mod_exit: state cleared");
}

void init_assets(void) {
	LOGI("Start of Assets Override");
}