#include "lua.h"
#include "lauxlib.h"
#include "PathParser.h"
#include "log.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <time.h>
#include <utime.h>

#define LOG_TAG "LuaFS"
#define DIR_MT "LuaDir"

static int push_error(lua_State *L) {
	lua_pushnil(L);
	lua_pushstring(L, strerror(errno));
	return 2;
}

static const char *real_path(const char *vpath) {
	return parse_path(vpath, NULL);
}

static int fs_mkdir(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *real = real_path(path);
	if (mkdir(real, 0770) != 0)
		return push_error(L);
	lua_pushboolean(L, 1);
	return 1;
}

static int fs_rmdir(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *real = real_path(path);
	if (rmdir(real) != 0)
		return push_error(L);
	lua_pushboolean(L, 1);
	return 1;
}

static void push_st_mode(lua_State *L, mode_t mode) {
	const char *t = "other";
	if (S_ISREG(mode)) t = "file";
	else if (S_ISDIR(mode)) t = "directory";
	else if (S_ISLNK(mode)) t = "link";
	else if (S_ISSOCK(mode)) t = "socket";
	else if (S_ISFIFO(mode)) t = "named pipe";
	else if (S_ISCHR(mode)) t = "char device";
	else if (S_ISBLK(mode)) t = "block device";
	lua_pushstring(L, t);
}

static int push_attr(lua_State *L, const struct stat *st, const char *name) {
	if (!name) {
		lua_newtable(L);
		lua_pushstring(L, "mode");
		push_st_mode(L, st->st_mode);
		lua_rawset(L, -3);
		lua_pushstring(L, "dev");
		lua_pushnumber(L, (lua_Number)st->st_dev);
		lua_rawset(L, -3);
		lua_pushstring(L, "ino");
		lua_pushnumber(L, (lua_Number)st->st_ino);
		lua_rawset(L, -3);
		lua_pushstring(L, "nlink");
		lua_pushnumber(L, (lua_Number)st->st_nlink);
		lua_rawset(L, -3);
		lua_pushstring(L, "uid");
		lua_pushnumber(L, (lua_Number)st->st_uid);
		lua_rawset(L, -3);
		lua_pushstring(L, "gid");
		lua_pushnumber(L, (lua_Number)st->st_gid);
		lua_rawset(L, -3);
		lua_pushstring(L, "rdev");
		lua_pushnumber(L, (lua_Number)st->st_rdev);
		lua_rawset(L, -3);
		lua_pushstring(L, "access");
		lua_pushnumber(L, (lua_Number)st->st_atime);
		lua_rawset(L, -3);
		lua_pushstring(L, "modification");
		lua_pushnumber(L, (lua_Number)st->st_mtime);
		lua_rawset(L, -3);
		lua_pushstring(L, "change");
		lua_pushnumber(L, (lua_Number)st->st_ctime);
		lua_rawset(L, -3);
		lua_pushstring(L, "size");
		lua_pushnumber(L, (lua_Number)st->st_size);
		lua_rawset(L, -3);
		lua_pushstring(L, "blocks");
		lua_pushnumber(L, (lua_Number)st->st_blocks);
		lua_rawset(L, -3);
		lua_pushstring(L, "blksize");
		lua_pushnumber(L, (lua_Number)st->st_blksize);
		lua_rawset(L, -3);
		lua_pushstring(L, "permissions");
		{
			char perm[16];
			snprintf(perm, sizeof(perm), "%o", st->st_mode & 0777);
			lua_pushstring(L, perm);
		}
		lua_rawset(L, -3);
		return 1;
	}

	if (!strcmp(name, "mode")) {
		push_st_mode(L, st->st_mode);
		return 1;
	}
	if (!strcmp(name, "dev")) {
		lua_pushnumber(L, (lua_Number)st->st_dev);
		return 1;
	}
	if (!strcmp(name, "ino")) {
		lua_pushnumber(L, (lua_Number)st->st_ino);
		return 1;
	}
	if (!strcmp(name, "nlink")) {
		lua_pushnumber(L, (lua_Number)st->st_nlink);
		return 1;
	}
	if (!strcmp(name, "uid")) {
		lua_pushnumber(L, (lua_Number)st->st_uid);
		return 1;
	}
	if (!strcmp(name, "gid")) {
		lua_pushnumber(L, (lua_Number)st->st_gid);
		return 1;
	}
	if (!strcmp(name, "rdev")) {
		lua_pushnumber(L, (lua_Number)st->st_rdev);
		return 1;
	}
	if (!strcmp(name, "access")) {
		lua_pushnumber(L, (lua_Number)st->st_atime);
		return 1;
	}
	if (!strcmp(name, "modification")) {
		lua_pushnumber(L, (lua_Number)st->st_mtime);
		return 1;
	}
	if (!strcmp(name, "change")) {
		lua_pushnumber(L, (lua_Number)st->st_ctime);
		return 1;
	}
	if (!strcmp(name, "size")) {
		lua_pushnumber(L, (lua_Number)st->st_size);
		return 1;
	}
	if (!strcmp(name, "blocks")) {
		lua_pushnumber(L, (lua_Number)st->st_blocks);
		return 1;
	}
	if (!strcmp(name, "blksize")) {
		lua_pushnumber(L, (lua_Number)st->st_blksize);
		return 1;
	}
	if (!strcmp(name, "permissions")) {
		char perm[16];
		snprintf(perm, sizeof(perm), "%o", st->st_mode & 0777);
		lua_pushstring(L, perm);
		return 1;
	}
	return luaL_error(L, "invalid attribute name '%s'", name);
}

static int fs_attributes(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *attr = luaL_optstring(L, 2, NULL);
	const char *real = real_path(path);
	struct stat st;
	if (stat(real, &st) != 0)
		return push_error(L);
	return push_attr(L, &st, attr);
}

static int dir_iter(lua_State *L) {
	DIR **d = (DIR **)lua_touserdata(L, lua_upvalueindex(1));
	struct dirent *e;
	if (*d == NULL)
		return 0;
	e = readdir(*d);
	if (e == NULL) {
		closedir(*d);
		*d = NULL;
		return 0;
	}
	lua_pushstring(L, e->d_name);
	return 1;
}

static int dir_close(lua_State *L) {
	DIR **d = (DIR **)luaL_checkudata(L, 1, DIR_MT);
	if (*d) {
		closedir(*d);
		*d = NULL;
	}
	return 0;
}

static int dir_gc(lua_State *L) {
	return dir_close(L);
}

static int fs_dir(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *real = real_path(path);
	DIR *d = opendir(real);
	if (!d)
		return push_error(L);

	DIR **ud = (DIR **)lua_newuserdata(L, sizeof(DIR *));
	*ud = d;
	luaL_getmetatable(L, DIR_MT);
	lua_setmetatable(L, -2);

	lua_pushcclosure(L, dir_iter, 1);
	return 1;
}

static int fs_touch(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *real = real_path(path);
	time_t atime = (time_t)luaL_optnumber(L, 2, time(NULL));
	time_t mtime = (time_t)luaL_optnumber(L, 3, atime);
	struct utimbuf ut;
	ut.actime = atime;
	ut.modtime = mtime;
	if (utime(real, &ut) != 0)
		return push_error(L);
	lua_pushboolean(L, 1);
	return 1;
}

static const luaL_Reg fslib[] = {
	{"mkdir", fs_mkdir},
	{"rmdir", fs_rmdir},
	{"attributes", fs_attributes},
	{"dir", fs_dir},
	{"touch", fs_touch},
	{NULL, NULL}
};

static const luaL_Reg dir_mt[] = {
	{"__gc", dir_gc},
	{"close", dir_close},
	{NULL, NULL}
};

void API_register_fs(lua_State *L) {
	luaL_newmetatable(L, DIR_MT);
	luaL_register(L, NULL, dir_mt);
	lua_pop(L, 1);

	luaL_register(L, "fs", fslib);
}
