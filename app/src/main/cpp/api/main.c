#include "lua.h"
#include "hook.h"
#include "lauxlib.h"
#include "ProgramState.h"
#include "log.h"
#include "overlay/main.h"

#define LOG_TAG "ProgramState"

HOOK_SYMBOL(
	RegisterProgramLibrary,
	"_ZN5Caver12ProgramState22RegisterProgramLibraryEv",
	void, (ProgramState *this)
) {
	orig_RegisterProgramLibrary(this);
	lua_State *L = this->L;

	API_register_java_stuff(L);

	LOGD("Lua libraries registered.");
}

void init_API() {
	initAPI_java();
}