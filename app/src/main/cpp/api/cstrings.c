#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include "log.h"
#include "hook.h"
#include "stdstring.h"
#include "java.h"
#include "map.h"
#include "toml.h"
#include "lua.h"
#include "lauxlib.h"

#define LOG_TAG "CStrings"

static Map *g_map = NULL;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_hooks_registered = 0;

static const char *cstr_lookup(const char *data)
{
	if (!data || !*data)
		return NULL;

	pthread_mutex_lock(&g_lock);
	const char *rep = g_map ? (const char *)Map_Get(g_map, (void *)data) : NULL;
	pthread_mutex_unlock(&g_lock);
	return rep;
}

static const char *cstr_lookup_n(const char *data, size_t len)
{
	if (!data || len == 0)
		return NULL;

	if (data[len] == '\0' && strlen(data) == len)
		return cstr_lookup(data);

	char stack[256];
	char *tmp = NULL;
	const char *key;

	if (len < sizeof(stack)) {
		memcpy(stack, data, len);
		stack[len] = '\0';
		key = stack;
	} else {
		tmp = malloc(len + 1);
		if (!tmp) return NULL;
		memcpy(tmp, data, len);
		tmp[len] = '\0';
		key = tmp;
	}

	const char *rep = cstr_lookup(key);
	free(tmp);
	return rep;
}

HOOK_OFFSET(
	String_CTor,
	0x37bc60, 0x566bb8,
	void, (String **out, const char *data)
) {
	if (data == NULL || *data == '\0') {
		orig_String_CTor(out, data);
		return;
	}

	const char *rep = cstr_lookup(data);
	orig_String_CTor(out, rep ? rep : data);
}

HOOK_OFFSET(
	String_AppendImpl,
	0x379988, 0x567254,
	void, (String **self, const char *data, size_t len)
) {
	if (data == NULL || len == 0 || *data == '\0') {
		orig_String_AppendImpl(self, data, len);
		return;
	}

	const char *rep = cstr_lookup_n(data, len);
	if (rep)
		orig_String_AppendImpl(self, rep, strlen(rep));
	else
		orig_String_AppendImpl(self, data, len);
}

// assign at this offset is (const char *, size_t)
HOOK_OFFSET(
	String_Assign,
	0x37aa1c, 0x56918c,
	void, (String **self, const char *data, size_t len)
) {
	if (data == NULL || len == 0 || *data == '\0') {
		orig_String_Assign(self, data, len);
		return;
	}

	const char *rep = cstr_lookup_n(data, len);
	if (rep)
		orig_String_Assign(self, rep, strlen(rep));
	else
		orig_String_Assign(self, data, len);
}

static void cstrings_clear(void)
{
	pthread_mutex_lock(&g_lock);
	if (g_map)
		Map_ClearOwned(g_map);
	pthread_mutex_unlock(&g_lock);
}

static void cstrings_load_from_mod(void)
{
	cstrings_clear();

	const char *mod = java_current_mod_id();
	const char *external = java_external_files();
	if (!mod || !mod[0] || !external || !external[0]) {
		LOGD("cstrings: no mod / paths, map stays empty");
		return;
	}

	// Same location as properties.toml: <external>/mods/<mod>/cstrings.toml
	char path[512];
	snprintf(path, sizeof(path), "%s/mods/%s/cstrings.toml", external, mod);

	pthread_mutex_lock(&g_lock);
	if (!g_map)
		g_map = Map_Create(2);
	int n = toml_load_string_map(path, g_map);
	pthread_mutex_unlock(&g_lock);

	if (n < 0)
		LOGD("cstrings: no cstrings.toml for mod '%s'", mod);
	else
		LOGI("cstrings: %d replacement(s) active for '%s'", n, mod);
}

static int l_cstring_replace(lua_State *L)
{
	const char *key = luaL_checkstring(L, 1);
	const char *val = luaL_checkstring(L, 2);

	if (!key || !*key) return 0;

	pthread_mutex_lock(&g_lock);
	if (!g_map)
		g_map = Map_Create(2);
	Map_SetOwned(g_map, key, val);
	pthread_mutex_unlock(&g_lock);
	return 0;
}

static int l_cstring_pop(lua_State *L)
{
	const char *key = luaL_checkstring(L, 1);
	pthread_mutex_lock(&g_lock);
	if (g_map)
		Map_RemoveOwned(g_map, key);
	pthread_mutex_unlock(&g_lock);
	return 0;
}

static int l_cstring_get(lua_State *L)
{
	const char *key = luaL_checkstring(L, 1);
	pthread_mutex_lock(&g_lock);
	const char *val = g_map ? (const char *)Map_Get(g_map, (void *)key) : NULL;
	if (val)
		lua_pushstring(L, val);
	else
		lua_pushnil(L);
	pthread_mutex_unlock(&g_lock);
	return 1;
}

static int l_cstring_count(lua_State *L)
{
	pthread_mutex_lock(&g_lock);
	lua_pushinteger(L, (lua_Integer)Map_Count(g_map));
	pthread_mutex_unlock(&g_lock);
	return 1;
}

static int l_cstring_clear(lua_State *L)
{
	(void)L;
	cstrings_clear();
	return 0;
}

static const luaL_Reg cstring_lib[] = {
	{"Replace", l_cstring_replace},
	{"Pop",     l_cstring_pop},
	{"Get",     l_cstring_get},
	{"Count",   l_cstring_count},
	{"Clear",   l_cstring_clear},
	{NULL, NULL}
};

void API_register_cstring(lua_State *L)
{
	lua_newtable(L);
	for (int i = 0; cstring_lib[i].name != NULL; i++) {
		lua_pushcfunction(L, cstring_lib[i].func);
		lua_setfield(L, -2, cstring_lib[i].name);
	}
	lua_setglobal(L, "CString");
	LOGD("CString exclusive API registered");
}

void cstrings_on_mod_exit(void)
{
	cstrings_clear();
	LOGD("cstrings: cleared on mod exit");
}

void initAPI_cstrings(void)
{
	if (!g_hooks_registered) {
		register_String_CTor();
		register_String_Assign();
		register_String_AppendImpl();
		g_hooks_registered = 1;
		LOGD("cstrings: hooks registered");
	}
	cstrings_load_from_mod();
}
