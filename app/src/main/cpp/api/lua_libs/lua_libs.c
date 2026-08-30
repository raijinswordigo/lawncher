#include "lua.h"
#include "lauxlib.h"
#include "lua_libs.h"
#include <math.h>
#include <stdlib.h>

// table

static int aux_getn(lua_State *L, int n) {
	luaL_checktype(L, n, LUA_TTABLE);
	return (int)lua_objlen(L, n);
}

static int t_foreachi(lua_State *L) {
	int n = aux_getn(L, 1);
	int i;
	luaL_checktype(L, 2, LUA_TFUNCTION);
	for (i = 1; i <= n; i++) {
		lua_pushvalue(L, 2);
		lua_pushinteger(L, i);
		lua_rawgeti(L, 1, i);
		lua_call(L, 2, 1);
		if (!lua_isnil(L, -1))
			return 1;
		lua_pop(L, 1);
	}
	return 0;
}

static int t_foreach(lua_State *L) {
	luaL_checktype(L, 1, LUA_TTABLE);
	luaL_checktype(L, 2, LUA_TFUNCTION);
	lua_pushnil(L);
	while (lua_next(L, 1)) {
		lua_pushvalue(L, 2);
		lua_pushvalue(L, -3);
		lua_pushvalue(L, -3);
		lua_call(L, 2, 1);
		if (!lua_isnil(L, -1))
			return 1;
		lua_pop(L, 2);
	}
	return 0;
}

static int t_getn(lua_State *L) {
	lua_pushinteger(L, aux_getn(L, 1));
	return 1;
}

static int t_setn(lua_State *L) {
	luaL_checktype(L, 1, LUA_TTABLE);
	luaL_error(L, "'setn' is obsolete");
	return 0;
}

static int t_insert(lua_State *L) {
	int e = aux_getn(L, 1) + 1;
	int pos;
	switch (lua_gettop(L)) {
		case 2:
			pos = e;
			break;
		case 3:
			pos = luaL_checkint(L, 2);
			if (pos > e) e = pos;
			luaL_argcheck(L, 1 <= pos && pos <= e, 2, "position out of bounds");
			for (int i = e; i > pos; i--) {
				lua_rawgeti(L, 1, i - 1);
				lua_rawseti(L, 1, i);
			}
			break;
		default:
			return luaL_error(L, "wrong number of arguments to 'insert'");
	}
	lua_rawseti(L, 1, pos);
	return 0;
}

static int t_remove(lua_State *L) {
	int size = aux_getn(L, 1);
	int pos = luaL_optint(L, 2, size);
	if (pos != size)
		luaL_argcheck(L, 1 <= pos && pos <= size + 1, 2, "position out of bounds");
	lua_rawgeti(L, 1, pos);
	for (; pos < size; pos++) {
		lua_rawgeti(L, 1, pos + 1);
		lua_rawseti(L, 1, pos);
	}
	lua_pushnil(L);
	lua_rawseti(L, 1, pos);
	return 1;
}

static int t_concat(lua_State *L) {
	luaL_Buffer b;
	size_t lsep;
	int i, last;
	const char *sep = luaL_optlstring(L, 2, "", &lsep);
	luaL_checktype(L, 1, LUA_TTABLE);
	i = luaL_optint(L, 3, 1);
	last = luaL_opt(L, luaL_checkint, 4, (int)lua_objlen(L, 1));
	luaL_buffinit(L, &b);
	for (; i < last; i++) {
		lua_rawgeti(L, 1, i);
		luaL_addvalue(&b);
		luaL_addlstring(&b, sep, lsep);
	}
	if (i == last) {
		lua_rawgeti(L, 1, i);
		luaL_addvalue(&b);
	}
	luaL_pushresult(&b);
	return 1;
}

static int t_maxn(lua_State *L) {
	lua_Number max = 0;
	luaL_checktype(L, 1, LUA_TTABLE);
	lua_pushnil(L);
	while (lua_next(L, 1)) {
		lua_pop(L, 1);
		if (lua_type(L, -1) == LUA_TNUMBER) {
			lua_Number v = lua_tonumber(L, -1);
			if (v > max) max = v;
		}
	}
	lua_pushnumber(L, max);
	return 1;
}

static void set2(lua_State *L, int i, int j) {
	lua_rawseti(L, 1, i);
	lua_rawseti(L, 1, j);
}

static int sort_comp(lua_State *L, int a, int b) {
	if (!lua_isnil(L, 2)) {
		int res;
		lua_pushvalue(L, 2);
		lua_pushvalue(L, a - 1);
		lua_pushvalue(L, b - 2);
		lua_call(L, 2, 1);
		res = lua_toboolean(L, -1);
		lua_pop(L, 1);
		return res;
	}
	else
		return lua_lessthan(L, a, b);
}

static void auxsort(lua_State *L, int l, int u) {
	while (l < u) {
		int i, j;
		lua_rawgeti(L, 1, l);
		lua_rawgeti(L, 1, u);
		if (sort_comp(L, -1, -2))
			set2(L, l, u);
		else
			lua_pop(L, 2);
		if (u - l == 1) break;
		i = (l + u) / 2;
		lua_rawgeti(L, 1, i);
		lua_rawgeti(L, 1, l);
		if (sort_comp(L, -2, -1))
			set2(L, i, l);
		else {
			lua_pop(L, 1);
			lua_rawgeti(L, 1, u);
			if (sort_comp(L, -1, -2))
				set2(L, i, u);
			else
				lua_pop(L, 2);
		}
		if (u - l == 2) break;
		lua_rawgeti(L, 1, i);
		lua_pushvalue(L, -1);
		lua_rawgeti(L, 1, u - 1);
		set2(L, i, u - 1);
		i = l; j = u - 1;
		for (;;) {
			while (lua_rawgeti(L, 1, ++i), sort_comp(L, -1, -2)) {
				if (i >= u) luaL_error(L, "invalid order function for sorting");
				lua_pop(L, 1);
			}
			while (lua_rawgeti(L, 1, --j), sort_comp(L, -3, -1)) {
				if (j <= l) luaL_error(L, "invalid order function for sorting");
				lua_pop(L, 1);
			}
			if (j < i) {
				lua_pop(L, 3);
				break;
			}
			set2(L, i, j);
		}
		lua_rawgeti(L, 1, u - 1);
		lua_rawgeti(L, 1, i);
		set2(L, u - 1, i);
		if (i - l < u - i) {
			j = l; i = i - 1; l = i + 2;
		} else {
			j = i + 1; i = u; u = j - 2;
		}
		auxsort(L, j, i);
	}
}

static int t_sort(lua_State *L) {
	int n = aux_getn(L, 1);
	luaL_checkstack(L, 40, "");
	if (!lua_isnoneornil(L, 2))
		luaL_checktype(L, 2, LUA_TFUNCTION);
	lua_settop(L, 2);
	auxsort(L, 1, n);
	return 0;
}

static const luaL_Reg tablelib[] = {
	{"concat",   t_concat},
	{"foreach",  t_foreach},
	{"foreachi", t_foreachi},
	{"getn",     t_getn},
	{"maxn",     t_maxn},
	{"insert",   t_insert},
	{"remove",   t_remove},
	{"setn",     t_setn},
	{"sort",     t_sort},
	{NULL, NULL}
};

// math

#undef PI
#define PI (3.14159265358979323846)
#define RADIANS_PER_DEGREE (PI / 180.0)

static int math_abs(lua_State *L) {
	lua_pushnumber(L, fabs(luaL_checknumber(L, 1)));
	return 1;
}

static int math_sin(lua_State *L) {
	lua_pushnumber(L, sin(luaL_checknumber(L, 1)));
	return 1;
}

static int math_sinh(lua_State *L) {
	lua_pushnumber(L, sinh(luaL_checknumber(L, 1)));
	return 1;
}

static int math_cos(lua_State *L) {
	lua_pushnumber(L, cos(luaL_checknumber(L, 1)));
	return 1;
}

static int math_cosh(lua_State *L) {
	lua_pushnumber(L, cosh(luaL_checknumber(L, 1)));
	return 1;
}

static int math_tan(lua_State *L) {
	lua_pushnumber(L, tan(luaL_checknumber(L, 1)));
	return 1;
}

static int math_tanh(lua_State *L) {
	lua_pushnumber(L, tanh(luaL_checknumber(L, 1)));
	return 1;
}

static int math_asin(lua_State *L) {
	lua_pushnumber(L, asin(luaL_checknumber(L, 1)));
	return 1;
}

static int math_acos(lua_State *L) {
	lua_pushnumber(L, acos(luaL_checknumber(L, 1)));
	return 1;
}

static int math_atan(lua_State *L) {
	lua_pushnumber(L, atan(luaL_checknumber(L, 1)));
	return 1;
}

static int math_atan2(lua_State *L) {
	lua_pushnumber(L, atan2(luaL_checknumber(L, 1), luaL_checknumber(L, 2)));
	return 1;
}

static int math_ceil(lua_State *L) {
	lua_pushnumber(L, ceil(luaL_checknumber(L, 1)));
	return 1;
}

static int math_floor(lua_State *L) {
	lua_pushnumber(L, floor(luaL_checknumber(L, 1)));
	return 1;
}

static int math_fmod(lua_State *L) {
	lua_pushnumber(L, fmod(luaL_checknumber(L, 1), luaL_checknumber(L, 2)));
	return 1;
}

static int math_modf(lua_State *L) {
	double ip;
	double fp = modf(luaL_checknumber(L, 1), &ip);
	lua_pushnumber(L, ip);
	lua_pushnumber(L, fp);
	return 2;
}

static int math_sqrt(lua_State *L) {
	lua_pushnumber(L, sqrt(luaL_checknumber(L, 1)));
	return 1;
}

static int math_pow(lua_State *L) {
	lua_pushnumber(L, pow(luaL_checknumber(L, 1), luaL_checknumber(L, 2)));
	return 1;
}

static int math_log(lua_State *L) {
	lua_pushnumber(L, log(luaL_checknumber(L, 1)));
	return 1;
}

static int math_log10(lua_State *L) {
	lua_pushnumber(L, log10(luaL_checknumber(L, 1)));
	return 1;
}

static int math_exp(lua_State *L) {
	lua_pushnumber(L, exp(luaL_checknumber(L, 1)));
	return 1;
}

static int math_deg(lua_State *L) {
	lua_pushnumber(L, luaL_checknumber(L, 1) / RADIANS_PER_DEGREE);
	return 1;
}

static int math_rad(lua_State *L) {
	lua_pushnumber(L, luaL_checknumber(L, 1) * RADIANS_PER_DEGREE);
	return 1;
}

static int math_frexp(lua_State *L) {
	int e;
	lua_pushnumber(L, frexp(luaL_checknumber(L, 1), &e));
	lua_pushinteger(L, e);
	return 2;
}

static int math_ldexp(lua_State *L) {
	lua_pushnumber(L, ldexp(luaL_checknumber(L, 1), luaL_checkint(L, 2)));
	return 1;
}

static int math_min(lua_State *L) {
	int n = lua_gettop(L);
	lua_Number dmin = luaL_checknumber(L, 1);
	int i;
	for (i = 2; i <= n; i++) {
		lua_Number d = luaL_checknumber(L, i);
		if (d < dmin)
			dmin = d;
	}
	lua_pushnumber(L, dmin);
	return 1;
}

static int math_max(lua_State *L) {
	int n = lua_gettop(L);
	lua_Number dmax = luaL_checknumber(L, 1);
	int i;
	for (i = 2; i <= n; i++) {
		lua_Number d = luaL_checknumber(L, i);
		if (d > dmax)
			dmax = d;
	}
	lua_pushnumber(L, dmax);
	return 1;
}

static int math_random(lua_State *L) {
	lua_Number r = (lua_Number)(rand() % RAND_MAX) / (lua_Number)RAND_MAX;
	switch (lua_gettop(L)) {
		case 0:
			lua_pushnumber(L, r);
			break;
		case 1: {
			int u = luaL_checkint(L, 1);
			luaL_argcheck(L, 1 <= u, 1, "interval is empty");
			lua_pushnumber(L, floor(r * u) + 1);
			break;
		}
		case 2: {
			int l = luaL_checkint(L, 1);
			int u = luaL_checkint(L, 2);
			luaL_argcheck(L, l <= u, 2, "interval is empty");
			lua_pushnumber(L, floor(r * (u - l + 1)) + l);
			break;
		}
		default:
			return luaL_error(L, "wrong number of arguments");
	}
	return 1;
}

static int math_randomseed(lua_State *L) {
	srand(luaL_checkint(L, 1));
	return 0;
}

static const luaL_Reg mathlib[] = {
	{"abs",        math_abs},
	{"acos",       math_acos},
	{"asin",       math_asin},
	{"atan",       math_atan},
	{"atan2",      math_atan2},
	{"ceil",       math_ceil},
	{"cos",        math_cos},
	{"cosh",       math_cosh},
	{"deg",        math_deg},
	{"exp",        math_exp},
	{"floor",      math_floor},
	{"fmod",       math_fmod},
	{"frexp",      math_frexp},
	{"ldexp",      math_ldexp},
	{"log",        math_log},
	{"log10",      math_log10},
	{"max",        math_max},
	{"min",        math_min},
	{"mod",        math_fmod},
	{"modf",       math_modf},
	{"pow",        math_pow},
	{"rad",        math_rad},
	{"random",     math_random},
	{"randomseed", math_randomseed},
	{"sin",        math_sin},
	{"sinh",       math_sinh},
	{"sqrt",       math_sqrt},
	{"tan",        math_tan},
	{"tanh",       math_tanh},
	{NULL, NULL}
};

/* Don't change this name! */
void API_register_lualibs(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; tablelib[i].name != NULL; i++) {
		lua_pushcfunction(L, tablelib[i].func);
		lua_setfield(L, -2, tablelib[i].name);
	}
	lua_setglobal(L, "table");

	lua_newtable(L);
	for (int i = 0; mathlib[i].name != NULL; i++) {
		lua_pushcfunction(L, mathlib[i].func);
		lua_setfield(L, -2, mathlib[i].name);
	}
	lua_pushnumber(L, PI);
	lua_setfield(L, -2, "pi");
	lua_pushnumber(L, HUGE_VAL);
	lua_setfield(L, -2, "huge");
	lua_setglobal(L, "math");
}