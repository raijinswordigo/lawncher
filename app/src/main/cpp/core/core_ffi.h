#ifndef LAWNCHER_CORE_FFI_H
#define LAWNCHER_CORE_FFI_H

#include "lua.h"
#include <stdbool.h>

#define FFI_MAX_ARGS 8

typedef enum {
	FFI_TYPE_VOID,
	FFI_TYPE_INT,
	FFI_TYPE_FLOAT,
	FFI_TYPE_DOUBLE,
	FFI_TYPE_BOOL,
	FFI_TYPE_POINTER,
	FFI_TYPE_VECTOR3,
	FFI_TYPE_VECTOR2,
	FFI_TYPE_QUATERNION,
	FFI_TYPE_MATRIX4,
	FFI_TYPE_FLOATCOLOR,
	FFI_TYPE_RECTANGLE,
	FFI_TYPE_STRING, // std::string (COW)
} ffi_type_t;

typedef struct {
	ffi_type_t arg_types[FFI_MAX_ARGS];
	bool       arg_is_ptr[FFI_MAX_ARGS];
	int        arg_count;
	ffi_type_t ret_type;
	bool       ret_is_ptr;
} ffi_signature_t;

ffi_signature_t ffi_parse_signature(const char *sig);

int caver_ffi_dispatch(lua_State *L, void *func_ptr, ffi_signature_t sig, int arg_base);

int ffi_lua_call(lua_State *L);

void *ffi_extract_ptr(lua_State *L, int idx);

#endif