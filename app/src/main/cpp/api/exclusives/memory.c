#include "lua.h"
#include "lauxlib.h"
#include "hook.h"
#include "types.h"
#include "stdstring.h"
#include "core_ffi.h"
#include "log.h"
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include "main/SceneObject.h"

#define LOG_TAG "KiwiMemory"
#define MEMORY_MT "Mini.MemoryAddress"

typedef struct {
	void *ptr;
} MemoryAddress;

#define CHECK_ADDR(L, i) ((MemoryAddress *)luaL_checkudata(L, i, MEMORY_MT))

#define DEFINE_INT_RW(NAME, CTYPE)                                     \
	static int m_read##NAME(lua_State *L) {                            \
		MemoryAddress *addr = CHECK_ADDR(L, 1);                        \
		lua_pushinteger(L, (lua_Integer)*(CTYPE *)addr->ptr);          \
		return 1;                                                      \
	}                                                                  \
	static int m_write##NAME(lua_State *L) {                           \
		MemoryAddress *addr = CHECK_ADDR(L, 1);                        \
		*(CTYPE *)addr->ptr = (CTYPE)luaL_checkinteger(L, 2);          \
		lua_settop(L, 1);                                              \
		return 1;                                                      \
	}

#define DEFINE_NUM_RW(NAME, CTYPE)                                     \
	static int m_read##NAME(lua_State *L) {                            \
		MemoryAddress *addr = CHECK_ADDR(L, 1);                        \
		lua_pushnumber(L, (lua_Number)*(CTYPE *)addr->ptr);            \
		return 1;                                                      \
	}                                                                  \
	static int m_write##NAME(lua_State *L) {                           \
		MemoryAddress *addr = CHECK_ADDR(L, 1);                        \
		*(CTYPE *)addr->ptr = (CTYPE)luaL_checknumber(L, 2);           \
		lua_settop(L, 1);                                              \
		return 1;                                                      \
	}

static MemoryAddress *push_addr(lua_State *L, void *ptr) {
	MemoryAddress *addr = (MemoryAddress *)lua_newuserdata(L, sizeof(*addr));
	addr->ptr = ptr;
	luaL_getmetatable(L, MEMORY_MT);
	lua_setmetatable(L, -2);
	lua_newtable(L);
	lua_setfenv(L, -2);
	return addr;
}

static float table_field_f(lua_State *L, int idx, const char *field) {
	if (!lua_istable(L, idx)) return 0.0f;
	lua_getfield(L, idx, field);
	float v = (float)lua_tonumber(L, -1);
	lua_pop(L, 1);
	return v;
}

static int m_index(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	if (!lua_isnil(L, -1))
		return 1;
	lua_pop(L, 2);

	luaL_getmetatable(L, MEMORY_MT);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	return 1;
}

static int m_newindex(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_pushvalue(L, 3);
	lua_settable(L, -3);
	return 0;
}

static int m_gc(lua_State *L) {
	(void)L;
	return 0;
}

static int m_add(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	lua_Integer offset = luaL_checkinteger(L, 2);
	push_addr(L, (char *)addr->ptr + (ptrdiff_t)offset);
	return 1;
}

static int m_sub(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	if (lua_isuserdata(L, 2)) {
		MemoryAddress *other = CHECK_ADDR(L, 2);
		lua_pushinteger(L, (lua_Integer)((char *)addr->ptr - (char *)other->ptr));
		return 1;
	}
	lua_Integer offset = luaL_checkinteger(L, 2);
	push_addr(L, (char *)addr->ptr - (ptrdiff_t)offset);
	return 1;
}

static int m_eq(lua_State *L) {
	MemoryAddress *a = CHECK_ADDR(L, 1);
	MemoryAddress *b = CHECK_ADDR(L, 2);
	lua_pushboolean(L, a->ptr == b->ptr);
	return 1;
}

static int m_tostring(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	char buf[32];
	snprintf(buf, sizeof(buf), "%p", addr->ptr);
	lua_pushstring(L, buf);
	return 1;
}

static int m_offset(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	lua_Integer offset = luaL_checkinteger(L, 2);
	push_addr(L, (char *)addr->ptr + (ptrdiff_t)offset);
	return 1;
}

static int m_getAddress(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	lua_pushlightuserdata(L, addr->ptr);
	return 1;
}

static int m_isNull(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	lua_pushboolean(L, addr->ptr == NULL);
	return 1;
}

static int m_free(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	if (addr->ptr) {
		free(addr->ptr);
		addr->ptr = NULL;
	}
	return 0;
}

static int m_readBool(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	lua_pushboolean(L, *(bool *)addr->ptr);
	return 1;
}
static int m_writeBool(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	*(bool *)addr->ptr = lua_toboolean(L, 2);
	lua_settop(L, 1);
	return 1;
}

DEFINE_INT_RW(Int8,  int8_t)
DEFINE_INT_RW(Int16, int16_t)
DEFINE_INT_RW(Int32, int32_t)
DEFINE_NUM_RW(Int64, int64_t)

DEFINE_INT_RW(UInt8,  uint8_t)
DEFINE_INT_RW(UInt16, uint16_t)
DEFINE_INT_RW(UInt32, uint32_t)
DEFINE_NUM_RW(UInt64, uint64_t)

DEFINE_NUM_RW(Float,  float)
DEFINE_NUM_RW(Double, double)

static int m_readPointer(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	push_addr(L, *(void **)addr->ptr);
	return 1;
}
static int m_writePointer(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	MemoryAddress *value = CHECK_ADDR(L, 2);
	*(void **)addr->ptr = value->ptr;
	lua_settop(L, 1);
	return 1;
}

static int m_readCString(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	const char *s = *(const char **)addr->ptr;
	if (s)
		lua_pushstring(L, s);
	else
		lua_pushnil(L);
	return 1;
}
static int m_writeCString(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	const char *s = luaL_checkstring(L, 2);
	*(const char **)addr->ptr = s;
	lua_settop(L, 1);
	return 1;
}

static int m_readString(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	String *s = *(String **)addr->ptr;
	if (s)
		lua_pushlstring(L, s, string_length(s));
	else
		lua_pushnil(L);
	return 1;
}

static int m_writeString(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	const char *s = luaL_checkstring(L, 2);
	String **slot = (String **)addr->ptr;
	String *old = *slot;

	String_create(slot, s);
	if (old)
		string_release(old);

	lua_settop(L, 1);
	return 1;
}

static int m_readVector3(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	Vector3 *v = (Vector3 *)addr->ptr;
	lua_newtable(L);
	lua_pushnumber(L, v->x); lua_setfield(L, -2, "x");
	lua_pushnumber(L, v->y); lua_setfield(L, -2, "y");
	lua_pushnumber(L, v->z); lua_setfield(L, -2, "z");
	return 1;
}

static int m_writeVector3(lua_State *L) {
	MemoryAddress *addr = CHECK_ADDR(L, 1);
	luaL_checktype(L, 2, LUA_TTABLE);
	Vector3 *v = (Vector3 *)addr->ptr;
	v->x = table_field_f(L, 2, "x");
	v->y = table_field_f(L, 2, "y");
	v->z = table_field_f(L, 2, "z");
	lua_settop(L, 1);
	return 1;
}

static int GetAddress(lua_State *L) {
	luaL_checkany(L, 1);
	const void *ptr = lua_topointer(L, 1);
	if (!ptr) {
		lua_pushnil(L);
		return 1;
	}
	push_addr(L, (void *)ptr);
	return 1;
}

static int Malloc(lua_State *L) {
	size_t size = (size_t)luaL_checkinteger(L, 1);
	if (size == 0) {
		push_addr(L, NULL);
		return 1;
	}
	void *ptr = malloc(size);
	if (!ptr)
		return luaL_error(L, "malloc failed for size %zu", size);
	memset(ptr, 0, size);
	push_addr(L, ptr);
	return 1;
}

static int Dlsym(lua_State *L) {
	const char *symbol = luaL_checkstring(L, 1);
	void *ptr = swordigo_dlsym(symbol);
	if (!ptr) {
		lua_pushnil(L);
		return 1;
	}
	push_addr(L, ptr);
	return 1;
}

static int GetComponentAddress(lua_State *L) {
	void **obj = luaL_checkudata(L, 1, "SceneObject");
	const char *interfaceSymbol = luaL_checkstring(L, 2);
	void *interface = swordigo_dlsym(interfaceSymbol);
	if (!interface) return 0;
	void *comp = SceneObject_ComponentWithInterface(*obj, interface);
	if (!comp) return 0;
	push_addr(L, comp);
	return 1;
}

static const luaL_Reg memory_methods[] = {
	/* metamethods */
	{"__gc",        m_gc},
	{"__add",       m_add},
	{"__sub",       m_sub},
	{"__eq",        m_eq},
	{"__tostring",  m_tostring},

	/* utility */
	{"offset",      m_offset},
	{"getAddress",  m_getAddress},
	{"isNull",      m_isNull},
	{"free",        m_free},

	/* bool */
	{"readBool",    m_readBool},
	{"writeBool",   m_writeBool},

	/* signed */
	{"readInt8",    m_readInt8},
	{"writeInt8",   m_writeInt8},
	{"readInt16",   m_readInt16},
	{"writeInt16",  m_writeInt16},
	{"readInt32",   m_readInt32},
	{"writeInt32",  m_writeInt32},
	{"readInt64",   m_readInt64},
	{"writeInt64",  m_writeInt64},

	/* unsigned */
	{"readUInt8",   m_readUInt8},
	{"writeUInt8",  m_writeUInt8},
	{"readUInt16",  m_readUInt16},
	{"writeUInt16", m_writeUInt16},
	{"readUInt32",  m_readUInt32},
	{"writeUInt32", m_writeUInt32},
	{"readUInt64",  m_readUInt64},
	{"writeUInt64", m_writeUInt64},

	/* floating point */
	{"readFloat",   m_readFloat},
	{"writeFloat",  m_writeFloat},
	{"readDouble",  m_readDouble},
	{"writeDouble", m_writeDouble},

	/* pointer */
	{"readPointer", m_readPointer},
	{"writePointer",m_writePointer},

	/* C strings */
	{"readCString", m_readCString},
	{"writeCString",m_writeCString},

	/* COW std::string */
	{"readString",  m_readString},
	{"writeString", m_writeString},

	/* Vector3 */
	{"readVector3", m_readVector3},
	{"writeVector3",m_writeVector3},

	/* FFI call (from core_ffi) */
	{"call",        ffi_lua_call},

	{NULL, NULL}
};

static const luaL_Reg memory_library[] = {
	{"GetAddress", GetAddress},
	{"GetComponentAddress", GetComponentAddress},
	{"Dlsym",      Dlsym},
	{"Malloc",     Malloc},
	{NULL, NULL}
};

void API_register_memory(lua_State *L) {
	luaL_newmetatable(L, MEMORY_MT);

	lua_pushcfunction(L, m_index);
	lua_setfield(L, -2, "__index");

	lua_pushcfunction(L, m_newindex);
	lua_setfield(L, -2, "__newindex");
	for (int i = 0; memory_methods[i].name; i++) {
		lua_pushcfunction(L, memory_methods[i].func);
		lua_setfield(L, -2, memory_methods[i].name);
	}

	lua_pop(L, 1);

	lua_newtable(L);
	for (int i = 0; memory_library[i].name != NULL; i++) {
		lua_pushcfunction(L, memory_library[i].func);
		lua_setfield(L, -2, memory_library[i].name);
	}
	lua_setglobal(L, "Memory");
}