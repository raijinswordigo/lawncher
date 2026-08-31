#include "lni.h"
#include "lua.h"
#include "lauxlib.h"
#include "main/GameController.h"

static int GetSpeed(lua_State *L) {
	lua_pushnumber(L, gsc_getSpeed());
	return 1;
}

static int SetSpeed(lua_State *L) {
	float speed = (float)lua_tonumber(L, 1);
	gsc_setSpeed(speed);
	return 0;
}

static const luaL_Reg lnilib[] = {
	{"GetSpeed", GetSpeed}
};

void API_register_lni(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; lnilib[i].name; i++) {
		lua_pushcfunction(L, lnilib[i].func);
		lua_setfield(L, -2, lnilib[i].name);
	}
	lua_setglobal(L, "LNI");
}