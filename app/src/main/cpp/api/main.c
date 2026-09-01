#include "lua.h"
#include "hook.h"
#include "lauxlib.h"

static int Test(lua_State *L) {
	lua_pushstring(L, "hello world");
	return 1;
}


static const luaL_Reg globals[] = {
	{"Testoo", Test},
	{NULL, NULL}
};

HOOK_SYMBOL(
	RegisterProgramLibrary,
	"_ZN5Caver12ProgramState22RegisterProgramLibraryEv",
	void, (void *this)
	) {
	orig_RegisterProgramLibrary(this);
	lua_State *L = *$(lua_State*, this, 0x0, 0x0);
	/* Avoid any stack pollution */
	const luaL_Reg *g = globals;
	for (; g->name; g++) {
		lua_pushcfunction(L, g->func);
		lua_setglobal(L, g->name);
	}
}