#include "lua.h"
#include "lauxlib.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dirent.h>
#include <errno.h>
#include "log.h"
#include "java.h"

#define LOG_TAG "FSApi"

#define FS_MAX_VPATH 480
#define FS_MAX_REAL  960
#define FS_MAX_FILE  (32 * 1024 * 1024) /* 32mb Limit */

typedef enum { FS_DENY = 0, FS_READ = 1, FS_RW = 2 } fs_access_t;

static void ensure_dir(const char *path) {
	char buf[FS_MAX_REAL];
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

static int path_has_traversal(const char *p) {
	for (const char *c = p; *c; c++) {
		if (c[0] == '.' && c[1] == '.' &&
		    (c == p || c[-1] == '/') &&
		    (c[2] == '/' || c[2] == '\0')) {
			return 1;
		}
	}
	return 0;
}

static int mod_root(char *out, size_t out_size) {
	const char *mod = java_current_mod_id();
	const char *external = java_external_files();
	if (!mod[0] || !external[0]) return 0;
	snprintf(out, out_size, "%s/mods/%s", external, mod);
	return 1;
}

/* does vpath fall under /mount or /mount/..., and if so what's left after it
 * (rel_out gets the remainder including its leading slash, or "" for an
 * exact match on the mount itself) */
static int mount_match(const char *vpath, const char *mount, char *rel_out, size_t rel_out_size) {
	size_t mlen = strlen(mount);
	if (strncmp(vpath, mount, mlen) != 0) return 0;
	if (vpath[mlen] == '\0') { rel_out[0] = '\0'; return 1; }
	if (vpath[mlen] != '/') return 0; /* "/resourcesX" must not match "/resources" */
	snprintf(rel_out, rel_out_size, "%s", vpath + mlen);
	return 1;
}

static int fs_resolve(const char *vpath, char *real, size_t real_size, fs_access_t *access) {
	if (!vpath || vpath[0] != '/') return 0;
	if (strlen(vpath) >= FS_MAX_VPATH) return 0;
	if (strstr(vpath, "\\") != NULL) return 0;
	if (strstr(vpath, "//") != NULL) return 0;
	if (path_has_traversal(vpath)) return 0;

	char root[FS_MAX_REAL];
	if (!mod_root(root, sizeof(root))) return 0;

	if (strcmp(vpath, "/properties.toml") == 0) {
		snprintf(real, real_size, "%s/properties.toml", root);
		*access = FS_READ;
		return 1;
	}
	if (strcmp(vpath, "/icon.png") == 0) {
		snprintf(real, real_size, "%s/icon.png", root);
		*access = FS_READ;
		return 1;
	}

	char rel[FS_MAX_VPATH];
	if (mount_match(vpath, "/resources", rel, sizeof(rel))) {
		snprintf(real, real_size, "%s/resources%s", root, rel);
		*access = FS_RW;
		return 1;
	}
	if (mount_match(vpath, "/kiwi", rel, sizeof(rel))) {
		snprintf(real, real_size, "%s/kiwi%s", root, rel);
		*access = FS_RW;
		return 1;
	}

	return 0; /* not a mount fs knows about - denied */
}

static int fail(lua_State *L, const char *fmt, const char *arg) {
	lua_pushnil(L);
	lua_pushfstring(L, fmt, arg);
	return 2;
}

static int l_fs_read(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid or inaccessible path '%s'", vpath);

	int fd = open(real, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
	if (fd < 0)
		return fail(L, "fs: cannot open '%s'", vpath);

	struct stat st;
	if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode)) {
		close(fd);
		return fail(L, "fs: '%s' is not a regular file", vpath);
	}
	if (st.st_size < 0 || st.st_size > FS_MAX_FILE) {
		close(fd);
		return fail(L, "fs: '%s' exceeds the max readable size", vpath);
	}

	size_t size = (size_t)st.st_size;
	char *buf = size ? malloc(size) : NULL;
	if (size && !buf) {
		close(fd);
		return fail(L, "fs: out of memory reading '%s'", vpath);
	}

	size_t total = 0;
	while (total < size) {
		ssize_t n = read(fd, buf + total, size - total);
		if (n <= 0) break;
		total += (size_t)n;
	}
	close(fd);

	if (total != size) {
		free(buf);
		return fail(L, "fs: short read on '%s'", vpath);
	}

	lua_pushlstring(L, buf, size);
	free(buf);
	return 1;
}

/* shared by write/append: makes sure the parent dir exists inside the mount */
static void ensure_parent(const char *real) {
	char parent[FS_MAX_REAL];
	snprintf(parent, sizeof(parent), "%s", real);
	char *slash = strrchr(parent, '/');
	if (slash) {
		*slash = '\0';
		ensure_dir(parent);
	}
}

static int l_fs_write(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	size_t len;
	const char *data = luaL_checklstring(L, 2, &len);

	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid path '%s'", vpath);
	if (access != FS_RW)
		return fail(L, "fs: '%s' is read-only", vpath);
	if (len > FS_MAX_FILE)
		return fail(L, "fs: write to '%s' exceeds the max file size", vpath);

	ensure_parent(real);

	/* write-to-temp then rename so a crash mid-write can never leave a
	 * half-written file sitting at the real path */
	char tmp[FS_MAX_REAL + 8];
	snprintf(tmp, sizeof(tmp), "%s.tmp", real);

	int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW | O_CLOEXEC, 0660);
	if (fd < 0)
		return fail(L, "fs: cannot open '%s' for write", vpath);

	size_t total = 0;
	int ok = 1;
	while (total < len) {
		ssize_t n = write(fd, data + total, len - total);
		if (n <= 0) { ok = 0; break; }
		total += (size_t)n;
	}
	close(fd);

	if (!ok || total != len) {
		remove(tmp);
		return fail(L, "fs: short write to '%s'", vpath);
	}
	if (rename(tmp, real) != 0) {
		remove(tmp);
		return fail(L, "fs: failed to commit '%s'", vpath);
	}

	lua_pushboolean(L, 1);
	return 1;
}

static int l_fs_append(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	size_t len;
	const char *data = luaL_checklstring(L, 2, &len);

	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid path '%s'", vpath);
	if (access != FS_RW)
		return fail(L, "fs: '%s' is read-only", vpath);

	struct stat st;
	size_t existing = (stat(real, &st) == 0 && S_ISREG(st.st_mode)) ? (size_t)st.st_size : 0;
	if (existing + len > FS_MAX_FILE)
		return fail(L, "fs: append to '%s' would exceed the max file size", vpath);

	ensure_parent(real);

	int fd = open(real, O_WRONLY | O_CREAT | O_APPEND | O_NOFOLLOW | O_CLOEXEC, 0660);
	if (fd < 0)
		return fail(L, "fs: cannot open '%s' for append", vpath);

	size_t total = 0;
	int ok = 1;
	while (total < len) {
		ssize_t n = write(fd, data + total, len - total);
		if (n <= 0) { ok = 0; break; }
		total += (size_t)n;
	}
	close(fd);

	if (!ok)
		return fail(L, "fs: short append to '%s'", vpath);

	lua_pushboolean(L, 1);
	return 1;
}

static int l_fs_exists(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access)) {
		lua_pushboolean(L, 0);
		return 1;
	}
	struct stat st;
	lua_pushboolean(L, stat(real, &st) == 0);
	return 1;
}

static int l_fs_remove(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid path '%s'", vpath);
	if (access != FS_RW)
		return fail(L, "fs: '%s' is read-only", vpath);

	struct stat st;
	if (lstat(real, &st) != 0)
		return fail(L, "fs: '%s' does not exist", vpath);
	if (S_ISLNK(st.st_mode))
		return fail(L, "fs: refusing to touch a symlink at '%s'", vpath);

	int rc = S_ISDIR(st.st_mode) ? rmdir(real) : remove(real);
	if (rc != 0)
		return fail(L, "fs: failed to remove '%s'", vpath);

	lua_pushboolean(L, 1);
	return 1;
}

static int l_fs_mkdir(lua_State *L) {
	const char *vpath = luaL_checkstring(L, 1);
	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid path '%s'", vpath);
	if (access != FS_RW)
		return fail(L, "fs: '%s' is read-only", vpath);

	ensure_dir(real);

	struct stat st;
	if (stat(real, &st) != 0 || !S_ISDIR(st.st_mode))
		return fail(L, "fs: failed to create '%s'", vpath);

	lua_pushboolean(L, 1);
	return 1;
}

static int l_fs_list(lua_State *L) {
	const char *vpath = luaL_optstring(L, 1, "/resources");
	char real[FS_MAX_REAL];
	fs_access_t access;
	if (!fs_resolve(vpath, real, sizeof(real), &access))
		return fail(L, "fs: invalid path '%s'", vpath);
	if (access == FS_READ)
		return fail(L, "fs: '%s' is a file, not a directory mount", vpath);

	DIR *dir = opendir(real);
	lua_newtable(L);
	if (!dir) return 1; /* mount not created yet -> just empty, not an error */

	int i = 1;
	struct dirent *ent;
	while ((ent = readdir(dir)) != NULL) {
		if (ent->d_name[0] == '.') continue; /* skips "." ".." and dotfiles */

		char child[FS_MAX_REAL];
		snprintf(child, sizeof(child), "%s/%s", real, ent->d_name);
		struct stat st;
		if (stat(child, &st) != 0) continue;

		lua_newtable(L);
		lua_pushstring(L, ent->d_name);
		lua_setfield(L, -2, "name");
		lua_pushboolean(L, S_ISDIR(st.st_mode));
		lua_setfield(L, -2, "isDir");
		if (S_ISREG(st.st_mode)) {
			lua_pushinteger(L, (lua_Integer)st.st_size);
			lua_setfield(L, -2, "size");
		}
		lua_rawseti(L, -2, i++);
	}
	closedir(dir);
	return 1;
}

static const luaL_Reg FS[] = {
	{"read",   l_fs_read},
	{"write",  l_fs_write},
	{"append", l_fs_append},
	{"exists", l_fs_exists},
	{"remove", l_fs_remove},
	{"mkdir",  l_fs_mkdir},
	{"list",   l_fs_list},
	{NULL, NULL}
};

void API_register_fs(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; FS[i].name != NULL; i++) {
		lua_pushcfunction(L, FS[i].func);
		lua_setfield(L, -2, FS[i].name);
	}
	lua_setglobal(L, "fs");
}

void initAPI_fs(void) {
	char root[FS_MAX_REAL];
	if (!mod_root(root, sizeof(root))) {
		LOGD("fs: mod root not resolvable yet, mounts will lazy-create on first write");
		return;
	}
	char resources[FS_MAX_REAL], kiwi[FS_MAX_REAL];
	snprintf(resources, sizeof(resources), "%s/resources", root);
	snprintf(kiwi, sizeof(kiwi), "%s/kiwi", root);
	ensure_dir(resources);
	ensure_dir(kiwi);
	LOGD("fs mounts ready under %s", root);
}
