#include "apis.h"
#include "../core/hook.h"
#include "../lua/lua.h"
#include "../core/log.h"

#define LOG_TAG "KiwiAPI"

HOOK_SYMBOL(
	RegisterLibraries,
"_ZN5Caver12ProgramState22RegisterProgramLibraryEv",
void, (void *this)
) {
	lua_State *L = *$(lua_State*, this, 0x0, 0x0);
	lua_pushstring(L, "NULL");
	lua_setglobal(L, "yoyoyo");

	return orig_RegisterLibraries(this);
}

void initAPI_api() {
	LOGD("yo");
}