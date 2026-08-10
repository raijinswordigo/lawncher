#include "apis.h"
#include "../core/hook.h"
#include "../core/vfs.h"
#include "../lua/lua.h"
#include "../core/log.h"

#define LOG_TAG "KiwiAPI"

/*
 * Public mod-facing Lua surface — FROZEN contract:
 *   mod_name  ->  always the string "NULL" (the game's original value).
 *                 Existing mods may read it; its value never changes.
 *
 * Everything below is ADDITIVE — new names only, nothing existing is
 * altered. Mods that know about it get a small, read-only introspection
 * bridge to the launcher's VFS session:
 *
 *   lawncher.mod        string  active mod id, or "" when vanilla
 *   lawncher.instance   string  active instance id, or "" when unset
 *   lawncher.modded     boolean true when a mod is active this session
 *   lawncher.asset_exists(path) -> boolean, whether the active mod
 *                                  overrides an asset at that path
 */

static int l_lawncher_asset_exists(lua_State *L) {
	const char *path = lua_tostring(L, 1);
	lua_pushboolean(L, path != NULL && vfs_asset_exists(path));
	return 1;
}

/** Pushes the read-only `lawncher` table and registers it as a global. */
static void push_lawncher_table(lua_State *L) {
	const char *mod = vfs_mod_id();
	const char *inst = vfs_instance_id();

	lua_newtable(L);

	lua_pushstring(L, mod[0] ? mod : "");
	lua_setfield(L, -2, "mod");

	lua_pushstring(L, inst[0] ? inst : "");
	lua_setfield(L, -2, "instance");

	lua_pushboolean(L, mod[0] != '\0');
	lua_setfield(L, -2, "modded");

	lua_pushcclosure(L, l_lawncher_asset_exists, 0);
	lua_setfield(L, -2, "asset_exists");

	lua_setglobal(L, "lawncher");
}

HOOK_SYMBOL(
	RegisterLibraries,
"_ZN5Caver12ProgramState22RegisterProgramLibraryEv",
void, (void *this)
) {
	lua_State *L = *$(lua_State*, this, 0x0, 0x0);

	lua_pushstring(L, "NULL");
	lua_setglobal(L, "mod_name");

	// Additive bridge — strictly new names, safe for any existing mod.
	push_lawncher_table(L);

	return orig_RegisterLibraries(this);
}

void initAPI_api() {
	LOGD("yo");
}
