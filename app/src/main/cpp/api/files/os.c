#include "lua.h"
#include "lauxlib.h"
#include "PathParser.h"
#include "log.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "LuaOS"

static int push_error(lua_State *L) {
	lua_pushnil(L);
	lua_pushstring(L, strerror(errno));
	return 2;
}

static int os_remove(lua_State *L) {
	const char *path = luaL_checkstring(L, 1);
	const char *real = parse_path(path, NULL);
	if (remove(real) != 0)
		return push_error(L);
	lua_pushboolean(L, 1);
	return 1;
}

static int os_rename(lua_State *L) {
	const char *oldp = luaL_checkstring(L, 1);
	const char *newp = luaL_checkstring(L, 2);
	const char *rold = parse_path(oldp, NULL);
	char oldbuf[1024];
	snprintf(oldbuf, sizeof(oldbuf), "%s", rold);
	const char *rnew = parse_path(newp, NULL);
	if (rename(oldbuf, rnew) != 0)
		return push_error(L);
	lua_pushboolean(L, 1);
	return 1;
}

static int os_tmpname(lua_State *L) {
	char buf[] = "/data/local/tmp/lua_XXXXXX";
	int fd = mkstemp(buf);
	if (fd < 0)
		return push_error(L);
	close(fd);
	lua_pushstring(L, buf);
	return 1;
}

static int os_clock(lua_State *L) {
	lua_pushnumber(L, ((lua_Number)clock()) / (lua_Number)CLOCKS_PER_SEC);
	return 1;
}

static int os_time(lua_State *L) {
	time_t t;
	if (lua_isnoneornil(L, 1)) {
		t = time(NULL);
	} else {
		luaL_checktype(L, 1, LUA_TTABLE);
		struct tm ts;
		memset(&ts, 0, sizeof(ts));
		lua_getfield(L, 1, "sec");
		ts.tm_sec = (int)luaL_optinteger(L, -1, 0);
		lua_pop(L, 1);
		lua_getfield(L, 1, "min");
		ts.tm_min = (int)luaL_optinteger(L, -1, 0);
		lua_pop(L, 1);
		lua_getfield(L, 1, "hour");
		ts.tm_hour = (int)luaL_optinteger(L, -1, 12);
		lua_pop(L, 1);
		lua_getfield(L, 1, "day");
		ts.tm_mday = (int)luaL_optinteger(L, -1, 1);
		lua_pop(L, 1);
		lua_getfield(L, 1, "month");
		ts.tm_mon = (int)luaL_optinteger(L, -1, 1) - 1;
		lua_pop(L, 1);
		lua_getfield(L, 1, "year");
		ts.tm_year = (int)luaL_optinteger(L, -1, 1900) - 1900;
		lua_pop(L, 1);
		lua_getfield(L, 1, "isdst");
		ts.tm_isdst = lua_isnil(L, -1) ? -1 : (lua_toboolean(L, -1) ? 1 : 0);
		lua_pop(L, 1);
		t = mktime(&ts);
		if (t == (time_t)(-1))
			return push_error(L);
	}
	lua_pushnumber(L, (lua_Number)t);
	return 1;
}

static int os_difftime(lua_State *L) {
	lua_pushnumber(L, difftime((time_t)luaL_checknumber(L, 1),
	                           (time_t)luaL_optnumber(L, 2, 0)));
	return 1;
}

static int os_date(lua_State *L) {
	const char *s = luaL_optstring(L, 1, "%c");
	time_t t = (time_t)luaL_optnumber(L, 2, time(NULL));
	struct tm *stm;
	if (*s == '!') {
		stm = gmtime(&t);
		s++;
	} else {
		stm = localtime(&t);
	}
	if (!stm)
		return push_error(L);
	if (strcmp(s, "*t") == 0) {
		lua_createtable(L, 0, 9);
		lua_pushinteger(L, stm->tm_sec);
		lua_setfield(L, -2, "sec");
		lua_pushinteger(L, stm->tm_min);
		lua_setfield(L, -2, "min");
		lua_pushinteger(L, stm->tm_hour);
		lua_setfield(L, -2, "hour");
		lua_pushinteger(L, stm->tm_mday);
		lua_setfield(L, -2, "day");
		lua_pushinteger(L, stm->tm_mon + 1);
		lua_setfield(L, -2, "month");
		lua_pushinteger(L, stm->tm_year + 1900);
		lua_setfield(L, -2, "year");
		lua_pushinteger(L, stm->tm_wday + 1);
		lua_setfield(L, -2, "wday");
		lua_pushinteger(L, stm->tm_yday + 1);
		lua_setfield(L, -2, "yday");
		lua_pushboolean(L, stm->tm_isdst);
		lua_setfield(L, -2, "isdst");
	} else {
		char cc[256];
		size_t res = strftime(cc, sizeof(cc), s, stm);
		lua_pushlstring(L, cc, res);
	}
	return 1;
}

static int os_exit(lua_State *L) {
	exit(luaL_optint(L, 1, EXIT_SUCCESS));
	return 0;
}

static const luaL_Reg oslib[] = {
	{"remove", os_remove},
	{"rename", os_rename},
	{"tmpname", os_tmpname},
	{"clock", os_clock},
	{"time", os_time},
	{"difftime", os_difftime},
	{"date", os_date},
	{"exit", os_exit},
	{NULL, NULL}
};

void API_register_os(lua_State *L) {
	luaL_register(L, "os", oslib);
}
