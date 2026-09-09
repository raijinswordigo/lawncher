#include "lua.h"
#include "lauxlib.h"

// I'm making this for the *fourth* time ever...

/* Attributes */
#define WALK_SPEED_OFFSET archSplit(0x0, 0x0);



static const luaL_Reg lib[] = {
{NULL, NULL}
};

void miniLL_register_character(lua_State *L) {
	lua_newtable(L);

	for (int i = 0; lib[i].name; i++) {
		lua_pushcfunction(L, lib[i].func);
		lua_setfield(L, -2, lib[i].name);
	}

	// -1 = Character, and -2 is Mini
	lua_setfield(L, -2, "Character");
	// Now Mini should be on the top!
}