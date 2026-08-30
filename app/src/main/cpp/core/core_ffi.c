#include "core_ffi.h"
#include "lauxlib.h"
#include "types.h"
#include "stdstring.h"
#include <ffi.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

static ffi_type_t parse_type(char c) {
	switch (c) {
		case 'v': return FFI_TYPE_VOID;
		case 'i': return FFI_TYPE_INT;
		case 'f': return FFI_TYPE_FLOAT;
		case 'd': return FFI_TYPE_DOUBLE;
		case 'b': return FFI_TYPE_BOOL;
		case 'p': return FFI_TYPE_POINTER;
		case 'V': return FFI_TYPE_VECTOR3;
		case '2': return FFI_TYPE_VECTOR2;
		case 'Q': return FFI_TYPE_QUATERNION;
		case 'M': return FFI_TYPE_MATRIX4;
		case 'C': return FFI_TYPE_FLOATCOLOR;
		case 'R': return FFI_TYPE_RECTANGLE;
		case 'S': return FFI_TYPE_STRING;
		default:  return FFI_TYPE_VOID;
	}
}

static void parse_one(const char **pp, ffi_type_t *type_out, bool *ptr_out) {
	*type_out = parse_type(**pp);
	(*pp)++;
	*ptr_out = (**pp == '*');
	if (*ptr_out) (*pp)++;
}

ffi_signature_t ffi_parse_signature(const char *sig) {
	ffi_signature_t result = {0};

	const char *colon = strchr(sig, ':');
	if (!colon) return result;

	const char *p = sig;
	int arg_idx = 0;
	while (p < colon && arg_idx < FFI_MAX_ARGS) {
		parse_one(&p, &result.arg_types[arg_idx], &result.arg_is_ptr[arg_idx]);
		arg_idx++;
	}
	result.arg_count = arg_idx;

	const char *rp = colon + 1;
	if (*rp) {
		parse_one(&rp, &result.ret_type, &result.ret_is_ptr);
	} else {
		result.ret_type = FFI_TYPE_VOID;
	}

	return result;
}

// descriptors.

static ffi_type *vector2_elements[]    = { &ffi_type_float, &ffi_type_float, NULL };
static ffi_type *vector3_elements[]    = { &ffi_type_float, &ffi_type_float, &ffi_type_float, NULL };
static ffi_type *quaternion_elements[] = { &ffi_type_float, &ffi_type_float, &ffi_type_float, &ffi_type_float, NULL };
static ffi_type *floatcolor_elements[] = { &ffi_type_float, &ffi_type_float, &ffi_type_float, &ffi_type_float, NULL };
static ffi_type *rectangle_elements[]  = { &ffi_type_float, &ffi_type_float, &ffi_type_float, &ffi_type_float, NULL };
static ffi_type *matrix4_elements[17]; /* 16 floats + NULL terminator */

static ffi_type ffi_struct_vector2    = { 0, 0, FFI_TYPE_STRUCT, vector2_elements };
static ffi_type ffi_struct_vector3    = { 0, 0, FFI_TYPE_STRUCT, vector3_elements };
static ffi_type ffi_struct_quaternion = { 0, 0, FFI_TYPE_STRUCT, quaternion_elements };
static ffi_type ffi_struct_floatcolor = { 0, 0, FFI_TYPE_STRUCT, floatcolor_elements };
static ffi_type ffi_struct_rectangle  = { 0, 0, FFI_TYPE_STRUCT, rectangle_elements };
static ffi_type ffi_struct_matrix4    = { 0, 0, FFI_TYPE_STRUCT, matrix4_elements };

static bool g_struct_types_ready = false;

static void init_struct_types(void) {
	if (g_struct_types_ready) return;
	for (int i = 0; i < 16; i++)
		matrix4_elements[i] = &ffi_type_float;
	matrix4_elements[16] = NULL;
	g_struct_types_ready = true;
}

static ffi_type *libffi_type_for(ffi_type_t t, bool is_ptr) {
	if (is_ptr)
		return &ffi_type_pointer;

	switch (t) {
		case FFI_TYPE_VOID:       return &ffi_type_void;
		case FFI_TYPE_INT:        return &ffi_type_sint32;
		case FFI_TYPE_FLOAT:      return &ffi_type_float;
		case FFI_TYPE_DOUBLE:     return &ffi_type_double;
		case FFI_TYPE_BOOL:       return &ffi_type_uint8;
		case FFI_TYPE_POINTER:    return &ffi_type_pointer;
		case FFI_TYPE_VECTOR2:    return &ffi_struct_vector2;
		case FFI_TYPE_VECTOR3:    return &ffi_struct_vector3;
		case FFI_TYPE_QUATERNION: return &ffi_struct_quaternion;
		case FFI_TYPE_FLOATCOLOR: return &ffi_struct_floatcolor;
		case FFI_TYPE_RECTANGLE:  return &ffi_struct_rectangle;
		case FFI_TYPE_MATRIX4:    return &ffi_struct_matrix4;
		case FFI_TYPE_STRING:     return &ffi_type_pointer;
		default:                  return &ffi_type_void;
	}
}

static size_t type_storage_size(ffi_type_t t, bool is_ptr) {
	if (is_ptr)
		return sizeof(void *);
	switch (t) {
		case FFI_TYPE_VOID:       return 0;
		case FFI_TYPE_INT:        return sizeof(int32_t);
		case FFI_TYPE_FLOAT:      return sizeof(float);
		case FFI_TYPE_DOUBLE:     return sizeof(double);
		case FFI_TYPE_BOOL:       return sizeof(uint8_t);
		case FFI_TYPE_POINTER:    return sizeof(void *);
		case FFI_TYPE_VECTOR2:    return sizeof(Vector2);
		case FFI_TYPE_VECTOR3:    return sizeof(Vector3);
		case FFI_TYPE_QUATERNION: return sizeof(Quaternion);
		case FFI_TYPE_FLOATCOLOR: return sizeof(FloatColor);
		case FFI_TYPE_RECTANGLE:  return sizeof(Rectangle);
		case FFI_TYPE_MATRIX4:    return sizeof(Matrix4);
		case FFI_TYPE_STRING:     return sizeof(String *);
		default:                  return 0;
	}
}

void *ffi_extract_ptr(lua_State *L, int idx) {
	if (lua_isnil(L, idx))
		return NULL;
	if (lua_islightuserdata(L, idx))
		return lua_touserdata(L, idx);
	if (lua_isuserdata(L, idx)) {
		void *block = lua_touserdata(L, idx);
		if (lua_getmetatable(L, idx)) {
			lua_getfield(L, -1, "__ptr");
			if (!lua_isnil(L, -1)) {
				void *p = lua_touserdata(L, -1);
				lua_pop(L, 2);
				return p;
			}
			lua_pop(L, 2);
		}
		return block ? *(void **)block : NULL;
	}
	return NULL;
}

static float table_field_f(lua_State *L, int idx, const char *field) {
	if (!lua_istable(L, idx))
		return 0.0f;
	lua_getfield(L, idx, field);
	float v = (float)lua_tonumber(L, -1);
	lua_pop(L, 1);
	return v;
}

static void marshal_arg(lua_State *L, int idx, ffi_type_t type, bool is_ptr, void *out) {
	if (is_ptr) {
		*(void **)out = ffi_extract_ptr(L, idx);
		return;
	}

	switch (type) {
		case FFI_TYPE_INT:
			*(int32_t *)out = (int32_t)luaL_checkinteger(L, idx);
			break;
		case FFI_TYPE_FLOAT:
			*(float *)out = (float)luaL_checknumber(L, idx);
			break;
		case FFI_TYPE_DOUBLE:
			*(double *)out = luaL_checknumber(L, idx);
			break;
		case FFI_TYPE_BOOL:
			*(uint8_t *)out = lua_toboolean(L, idx) ? 1 : 0;
			break;
		case FFI_TYPE_POINTER:
			*(void **)out = ffi_extract_ptr(L, idx);
			break;
		case FFI_TYPE_VECTOR2: {
			Vector2 *v = (Vector2 *)out;
			v->x = table_field_f(L, idx, "x");
			v->y = table_field_f(L, idx, "y");
			break;
		}
		case FFI_TYPE_VECTOR3: {
			Vector3 *v = (Vector3 *)out;
			v->x = table_field_f(L, idx, "x");
			v->y = table_field_f(L, idx, "y");
			v->z = table_field_f(L, idx, "z");
			break;
		}
		case FFI_TYPE_QUATERNION: {
			Quaternion *q = (Quaternion *)out;
			q->x = table_field_f(L, idx, "x");
			q->y = table_field_f(L, idx, "y");
			q->z = table_field_f(L, idx, "z");
			q->w = table_field_f(L, idx, "w");
			break;
		}
		case FFI_TYPE_FLOATCOLOR: {
			FloatColor *c = (FloatColor *)out;
			c->r = table_field_f(L, idx, "r");
			c->g = table_field_f(L, idx, "g");
			c->b = table_field_f(L, idx, "b");
			c->a = table_field_f(L, idx, "a");
			break;
		}
		case FFI_TYPE_RECTANGLE: {
			Rectangle *r = (Rectangle *)out;
			r->x      = table_field_f(L, idx, "x");
			r->y      = table_field_f(L, idx, "y");
			r->width  = table_field_f(L, idx, "width");
			r->height = table_field_f(L, idx, "height");
			break;
		}
		case FFI_TYPE_MATRIX4: {
			float *m = (float *)out;
			if (lua_istable(L, idx)) {
				for (int i = 0; i < 16; i++) {
					lua_rawgeti(L, idx, i + 1);
					m[i] = (float)lua_tonumber(L, -1);
					lua_pop(L, 1);
				}
			} else {
				memset(m, 0, sizeof(float) * 16);
			}
			break;
		}
		case FFI_TYPE_STRING: {
			const char *s = luaL_checkstring(L, idx);
			String *cs = NULL;
			String_create(&cs, s);
			*(String **)out = cs;
			break;
		}
		default:
			break;
	}
}

static void push_table_f4(lua_State *L, const float *vals, const char *names[], int count) {
	lua_newtable(L);
	for (int i = 0; i < count; i++) {
		lua_pushnumber(L, vals[i]);
		lua_setfield(L, -2, names[i]);
	}
}

static void push_result(lua_State *L, ffi_type_t type, bool is_ptr, void *result) {
	if (is_ptr) {
		lua_pushlightuserdata(L, *(void **)result);
		return;
	}

	switch (type) {
		case FFI_TYPE_VOID:
			break;
		case FFI_TYPE_INT:
			lua_pushinteger(L, *(int32_t *)result);
			break;
		case FFI_TYPE_FLOAT:
			lua_pushnumber(L, *(float *)result);
			break;
		case FFI_TYPE_DOUBLE:
			lua_pushnumber(L, *(double *)result);
			break;
		case FFI_TYPE_BOOL:
			lua_pushboolean(L, *(uint8_t *)result != 0);
			break;
		case FFI_TYPE_POINTER:
			lua_pushlightuserdata(L, *(void **)result);
			break;
		case FFI_TYPE_VECTOR2: {
			Vector2 *v = (Vector2 *)result;
			const char *names[] = { "x", "y" };
			float vals[] = { v->x, v->y };
			push_table_f4(L, vals, names, 2);
			break;
		}
		case FFI_TYPE_VECTOR3: {
			Vector3 *v = (Vector3 *)result;
			const char *names[] = { "x", "y", "z" };
			float vals[] = { v->x, v->y, v->z };
			push_table_f4(L, vals, names, 3);
			break;
		}
		case FFI_TYPE_QUATERNION: {
			Quaternion *q = (Quaternion *)result;
			const char *names[] = { "x", "y", "z", "w" };
			float vals[] = { q->x, q->y, q->z, q->w };
			push_table_f4(L, vals, names, 4);
			break;
		}
		case FFI_TYPE_FLOATCOLOR: {
			FloatColor *c = (FloatColor *)result;
			const char *names[] = { "r", "g", "b", "a" };
			float vals[] = { c->r, c->g, c->b, c->a };
			push_table_f4(L, vals, names, 4);
			break;
		}
		case FFI_TYPE_RECTANGLE: {
			Rectangle *r = (Rectangle *)result;
			const char *names[] = { "x", "y", "width", "height" };
			float vals[] = { r->x, r->y, r->width, r->height };
			push_table_f4(L, vals, names, 4);
			break;
		}
		case FFI_TYPE_MATRIX4: {
			float *m = (float *)result;
			lua_newtable(L);
			for (int i = 0; i < 16; i++) {
				lua_pushnumber(L, m[i]);
				lua_rawseti(L, -2, i + 1);
			}
			break;
		}
		case FFI_TYPE_STRING: {
			String *cs = *(String **)result;
			if (cs) {
				lua_pushlstring(L, cs, string_length(cs));
			} else {
				lua_pushnil(L);
			}
			break;
		}
		default:
			lua_pushnil(L);
			break;
	}
}

#define FFI_MAX_RESULT_SIZE 128

int caver_ffi_dispatch(lua_State *L, void *func_ptr, ffi_signature_t sig, int arg_base) {
	init_struct_types();

	if (sig.arg_count > FFI_MAX_ARGS)
		return luaL_error(L, "ffi call: too many arguments (%d > %d)", sig.arg_count, FFI_MAX_ARGS);

	ffi_type *arg_types[FFI_MAX_ARGS];
	void     *arg_values[FFI_MAX_ARGS];
	uint8_t   arg_storage[FFI_MAX_ARGS][64];
	String   *owned_strings[FFI_MAX_ARGS] = {0};

	for (int i = 0; i < sig.arg_count; i++) {
		size_t needed = type_storage_size(sig.arg_types[i], sig.arg_is_ptr[i]);
		if (needed > sizeof(arg_storage[i]))
			return luaL_error(L, "ffi call: arg %d too large (%zu)", i, needed);

		if (sig.arg_types[i] == FFI_TYPE_STRING && !sig.arg_is_ptr[i]) {
			/*
			 * Exact match for the working native pattern:
			 *
			 *   String *mn;
			 *   String_create(&mn, name);
			 *   setModelName(comp, &mn);          // passes String**
			 *
			 * Store &owned_strings[i] (a String**) into arg_storage so
			 * that libffi loads that pointer value into the argument
			 * register.
			 */
			const char *s = luaL_checkstring(L, arg_base + i);
			String_create(&owned_strings[i], s);
			*(String ***)arg_storage[i] = &owned_strings[i];
			arg_types[i]  = &ffi_type_pointer;
			arg_values[i] = arg_storage[i];
		} else {
			arg_types[i]  = libffi_type_for(sig.arg_types[i], sig.arg_is_ptr[i]);
			marshal_arg(L, arg_base + i, sig.arg_types[i], sig.arg_is_ptr[i], arg_storage[i]);
			arg_values[i] = arg_storage[i];
		}
	}

	ffi_type *ret_libffi_type = libffi_type_for(sig.ret_type, sig.ret_is_ptr);

	ffi_cif cif;
	ffi_status status = ffi_prep_cif(&cif, FFI_DEFAULT_ABI,
	                                 (unsigned int)sig.arg_count,
	                                 ret_libffi_type, arg_types);
	if (status != FFI_OK)
		return luaL_error(L, "ffi call: ffi_prep_cif failed (status %d)", (int)status);

	union {
		uint8_t bytes[FFI_MAX_RESULT_SIZE];
		long double align;
	} result_buf;

	ffi_call(&cif, FFI_FN(func_ptr), &result_buf, arg_values);

	for (int i = 0; i < sig.arg_count; i++) {
		if (owned_strings[i])
			string_release(owned_strings[i]);
	}

	push_result(L, sig.ret_type, sig.ret_is_ptr, &result_buf);
	return sig.ret_type == FFI_TYPE_VOID ? 0 : 1;
}

int ffi_lua_call(lua_State *L) {
	void *func_ptr = ffi_extract_ptr(L, 1);
	if (!func_ptr)
		return luaL_error(L, "ffi call: invalid function address at arg 1");
	const char *sig_str = luaL_checkstring(L, 2);
	ffi_signature_t sig = ffi_parse_signature(sig_str);
	return caver_ffi_dispatch(L, func_ptr, sig, 3);
}