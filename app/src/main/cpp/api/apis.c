#include <string.h>
#include "apis.h"
#include "hook.h"
#include "lua.h"
#include "log.h"
#include "jstuff/main.h"
#include "java.h"
#include "lua_libs/lua_libs.h"

#define LOG_TAG "KiwiAPI"

extern int API_register_mini(lua_State *L);
extern void initAPI_mini();

extern void API_register_memory(lua_State *L);
extern void API_register_skeleton(lua_State *L);
extern void API_register_console(lua_State *L);
extern void API_register_cstring(lua_State *L);

extern void API_register_fs(lua_State *L);
extern void initAPI_fs();

HOOK_SYMBOL(
	RegisterLibraries,
	"_ZN5Caver12ProgramState22RegisterProgramLibraryEv",
	void, (void *this)
) {
	lua_State *L = *$(lua_State*, this, 0x0, 0x0);

	/* Lua Libs */
	API_register_lualibs(L);

	API_register_mini(L);
	API_register_fs(L);
	API_register_skeleton(L);

	API_register_console(L);

	/* Java Stuff! */
	API_register_java_stuff(L);

	/* Exclusives. */
	if (strstr(java_current_mod_id(), "net.kiwi") != NULL) {
		API_register_memory(L);
		API_register_cstring(L);
		/* API_register_map(L); */
	}

	return orig_RegisterLibraries(this);
}

void initAPI_api() {
	initAPI_mini();
	initAPI_fs();
	initAPI_java();
	LOGD("Initialized Kiwi API.");
}