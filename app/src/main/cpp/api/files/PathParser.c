#include "PathParser.h"
#include "java.h"
#include "log.h"
#include "stdstring.h"
#include "hook.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>

#define LOG_TAG "PathParser"

static char g_resolved[1024];

static int starts_with_ci(const char *s, const char *pre) {
	while (*pre) {
		if (tolower((unsigned char)*s) != tolower((unsigned char)*pre)) return 0;
		s++;
		pre++;
	}
	return 1;
}

static int is_readonly_root(const char *rel) {
	if (!rel || !*rel) return 0;
	if (!strcmp(rel, "properties.toml")) return 1;
	if (!strcmp(rel, "icon.png")) return 1;
	return 0;
}

static void ensure_dir(const char *path) {
	char buf[1024];
	snprintf(buf, sizeof(buf), "%s", path);
	for (char *p = buf + 1; *p; p++) {
		if (*p == '/') {
			*p = '\0';
			mkdir(buf, 0770);
			*p = '/';
		}
	}
}

static void normalize(char *s) {
	char *r = s;
	char *w = s;
	int slash = 0;
	while (*r) {
		if (*r == '\\') *r = '/';
		if (*r == '/') {
			if (!slash) {
				*w++ = '/';
				slash = 1;
			}
		} else {
			*w++ = *r;
			slash = 0;
		}
		r++;
	}
	*w = '\0';
	size_t n = strlen(s);
	if (n > 1 && s[n - 1] == '/')
		s[n - 1] = '\0';
}

static const char *mod_base(void) {
	const char *id = java_current_mod_id();
	static char base[512];
	if (!id || !*id) {
		base[0] = '\0';
		return base;
	}
	snprintf(base, sizeof(base), "%s/mods/%s/", java_external_files(), id);
	return base;
}

static const char *skip_prefix(const char *p, const char *pre) {
	size_t n = strlen(pre);
	if (!starts_with_ci(p, pre)) return NULL;
	p += n;
	while (*p == '/') p++;
	return p;
}

const char *parse_path(const char *vpath, int *readonly) {
	if (readonly) *readonly = 0;
	if (!vpath || !*vpath) {
		g_resolved[0] = '\0';
		return g_resolved;
	}

	char tmp[1024];
	snprintf(tmp, sizeof(tmp), "%s", vpath);
	normalize(tmp);

	const char *p = tmp;
	while (*p == '/') p++;

	const char *base = mod_base();
	if (!base[0]) {
		snprintf(g_resolved, sizeof(g_resolved), "%s", tmp);
		return g_resolved;
	}

	const char *rest;

	rest = skip_prefix(p, "ExternalFiles");
	if (rest) {
		if (starts_with_ci(rest, "Documents")) {
			rest = skip_prefix(rest, "Documents");
			if (!rest) rest = "";
			snprintf(g_resolved, sizeof(g_resolved), "%ssaves/%s", base, rest);
			return g_resolved;
		}
		snprintf(g_resolved, sizeof(g_resolved), "%sdata/%s", base, rest);
		return g_resolved;
	}

	rest = skip_prefix(p, "Files");
	if (rest) {
		if (starts_with_ci(rest, "Documents")) {
			rest = skip_prefix(rest, "Documents");
			if (!rest) rest = "";
			snprintf(g_resolved, sizeof(g_resolved), "%ssaves/%s", base, rest);
			return g_resolved;
		}
		snprintf(g_resolved, sizeof(g_resolved), "%sdata/%s", base, rest);
		return g_resolved;
	}

	rest = skip_prefix(p, "Saves");
	if (rest) {
		snprintf(g_resolved, sizeof(g_resolved), "%ssaves/%s", base, rest);
		return g_resolved;
	}

	rest = skip_prefix(p, "resources");
	if (rest) {
		snprintf(g_resolved, sizeof(g_resolved), "%sresources/%s", base, rest);
		return g_resolved;
	}

	rest = skip_prefix(p, "Resources");
	if (rest) {
		snprintf(g_resolved, sizeof(g_resolved), "%sresources/%s", base, rest);
		return g_resolved;
	}

	if (is_readonly_root(p)) {
		if (readonly) *readonly = 1;
		snprintf(g_resolved, sizeof(g_resolved), "%s%s", base, p);
		return g_resolved;
	}

	snprintf(g_resolved, sizeof(g_resolved), "%s%s", base, p);
	return g_resolved;
}

static int mode_is_write(const char *mode) {
	if (!mode) return 0;
	return strchr(mode, 'w') || strchr(mode, 'a') || strchr(mode, '+');
}

DL_SYMBOL(
	NewByteBufferFromAA,
	"_ZN5Caver29NewByteBufferFromAndroidAssetERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEEPj",
	void*, (String *file, uint *param_2)
);

FILE *parse_open(const char *vpath, const char *mode) {
	int readonly = 0;
	const char *real = parse_path(vpath, &readonly);

	LOGD("open '%s' -> '%s' mode=%s", vpath, real, mode ? mode : "?");

	if (readonly && mode_is_write(mode))
		return NULL;

	if (mode_is_write(mode))
		ensure_dir(real);

	FILE *f = fopen(real, mode);
	if (f) return f;

	const char *p = vpath;
	while (*p == '/' || *p == '\\') p++;
	if ((starts_with_ci(p, "resources/") || starts_with_ci(p, "Resources/") ||
	     starts_with_ci(p, "resources") || starts_with_ci(p, "Resources")) &&
	    !mode_is_write(mode)) {
		const char *rest = strchr(p, '/');
		if (!rest) rest = strchr(p, '\\');
		if (!rest) rest = p + strlen(p);
		else rest++;
		while (*rest == '/' || *rest == '\\') rest++;

		char assetname[512];
		snprintf(assetname, sizeof(assetname), "resources/%s", rest);

		String s;
		String_create(&s, assetname);
		uint sz = 0;
		void *buf = NewByteBufferFromAA(&s, &sz);
		String_destroy(&s);

		if (!buf || sz == 0) {
			if (buf) free(buf);
			return NULL;
		}

		FILE *mf = fmemopen(buf, sz, "rb");
		if (!mf) {
			free(buf);
			return NULL;
		}
		return mf;
	}

	return NULL;
}
