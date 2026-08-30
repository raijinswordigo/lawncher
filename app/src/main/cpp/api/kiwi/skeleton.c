#include "components.h"
#include "main/SceneObject.h"
#include "types.h"
#include "hook.h"
#include "log.h"
#include "lauxlib.h"
#include "lua.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "MiniSkeleton"
#define MAX_BONES 64

typedef struct {
	float offX, offY, offZ;
	float rotX, rotY, rotZ;
	float sX, sY, sZ;
} BoneOverride;

typedef struct BoneEntry {
	void *skeleton;
	BoneOverride bones[MAX_BONES];
	struct BoneEntry *next;
} BoneEntry;

static BoneEntry *g_bones = NULL;

static BoneEntry *find_entry(void *skeleton) {
	for (BoneEntry *e = g_bones; e; e = e->next)
		if (e->skeleton == skeleton) return e;
	return NULL;
}

static BoneEntry *get_entry(void *skeleton) {
	BoneEntry *e = find_entry(skeleton);
	if (e) return e;

	e = calloc(1, sizeof(BoneEntry));
	e->skeleton = skeleton;
	for (int i = 0; i < MAX_BONES; i++)
		e->bones[i] = (BoneOverride){0, 0, 0, 0, 0, 0, 1, 1, 1};
	e->next = g_bones;
	g_bones = e;
	return e;
}

static void remove_entry(void *skeleton) {
	BoneEntry **pp = &g_bones;
	while (*pp) {
		if ((*pp)->skeleton == skeleton) {
			BoneEntry *dead = *pp;
			*pp = dead->next;
			free(dead);
			return;
		}
		pp = &(*pp)->next;
	}
}

void clear_bone_map(void) {
	while (g_bones) {
		BoneEntry *n = g_bones->next;
		free(g_bones);
		g_bones = n;
	}
}

static void *get_skeleton_from_object(void *obj) {
	void *modelComp = SceneObject_ComponentWithInterface(obj, Model_Interface);
	if (!modelComp) return NULL;
	void *modelInst = *$(void*, modelComp, 0x48, 0x88);
	if (!modelInst) return NULL;
	return *$(void*, modelInst, 0x0, 0x18);
}

static void apply_scale(Matrix4 *mat, float sx, float sy, float sz) {
	for (int i = 0; i < 3; i++) mat->m[i]     *= sx;
	for (int i = 0; i < 3; i++) mat->m[4 + i] *= sy;
	for (int i = 0; i < 3; i++) mat->m[8 + i] *= sz;
}

static void offset_bone_position(void *bones, int idx, float x, float y, float z) {
	float *pos = (float *)((uintptr_t)bones + idx * 0xDC);
	pos[0] += x;
	pos[1] += y;
	pos[2] += z;
}

static void matrix_multiply(Matrix4 *result, const Matrix4 *a, const Matrix4 *b) {
	Matrix4 tmp;
	for (int i = 0; i < 4; i++)
		for (int j = 0; j < 4; j++) {
			tmp.m[i * 4 + j] = 0.f;
			for (int k = 0; k < 4; k++)
				tmp.m[i * 4 + j] += a->m[i * 4 + k] * b->m[k * 4 + j];
		}
	memcpy(result, &tmp, sizeof(Matrix4));
}

static void matrix_from_euler(Matrix4 *mat, float rx, float ry, float rz) {
	float pi = 3.14159265f;
	rx *= pi / 180.f; ry *= pi / 180.f; rz *= pi / 180.f;

	float cx = cosf(rx), sx = sinf(rx);
	float cy = cosf(ry), sy = sinf(ry);
	float cz = cosf(rz), sz = sinf(rz);

	mat->m[0]  =  cy * cz;
	mat->m[1]  = -cy * sz;
	mat->m[2]  =  sy;
	mat->m[3]  =  0;
	mat->m[4]  =  sx * sy * cz + cx * sz;
	mat->m[5]  = -sx * sy * sz + cx * cz;
	mat->m[6]  = -sx * cy;
	mat->m[7]  =  0;
	mat->m[8]  = -cx * sy * cz + sx * sz;
	mat->m[9]  =  cx * sy * sz + sx * cz;
	mat->m[10] =  cx * cy;
	mat->m[11] =  0;
	mat->m[12] =  0;
	mat->m[13] =  0;
	mat->m[14] =  0;
	mat->m[15] =  1;
}

static void apply_rotation(Matrix4 *mat, float rx, float ry, float rz) {
	float tx = mat->m[12], ty = mat->m[13], tz = mat->m[14];
	Matrix4 rot;
	matrix_from_euler(&rot, rx, ry, rz);
	matrix_multiply(mat, &rot, mat);
	mat->m[12] = tx;
	mat->m[13] = ty;
	mat->m[14] = tz;
}

HOOK_SYMBOL(
	SkeletonInstance_EvaluateMatrices,
	"_ZN5Caver16SkeletonInstance16EvaluateMatricesEv",
	void, (void *si)
) {
	BoneEntry *e = find_entry(si);
	if (!e) {
		orig_SkeletonInstance_EvaluateMatrices(si);
		return;
	}

	void *bones = *$(void*, si, 0x8, 0x10);
	int boneCount = *(int *)(*(uintptr_t *)si);
	if (!bones || boneCount <= 0) {
		orig_SkeletonInstance_EvaluateMatrices(si);
		return;
	}
	if (boneCount > MAX_BONES)
		boneCount = MAX_BONES;

	for (int i = 0; i < boneCount; i++) {
		BoneOverride *ov = &e->bones[i];
		offset_bone_position(bones, i, ov->offX, ov->offY, ov->offZ);
	}

	orig_SkeletonInstance_EvaluateMatrices(si);

	for (int i = 0; i < boneCount; i++) {
		BoneOverride *ov = &e->bones[i];
		Matrix4 *mat = (Matrix4 *)((uintptr_t)bones + i * 0xDC + 0x9C);
		apply_scale(mat, ov->sX, ov->sY, ov->sZ);
		apply_rotation(mat, ov->rotX, ov->rotY, ov->rotZ);
	}
}

DL_SYMBOL(
	BoneIndexForName,
	"_ZNK5Caver8Skeleton16BoneIndexForNameERKSs",
	int, (void *thiz, const void *name)
	);

// lua

typedef struct {
	void *skeleton;
} MiniSkeleton;

#define MINI_SKELETON_MT "MiniSkeleton"

static MiniSkeleton *check_skeleton(lua_State *L, int idx) {
	return (MiniSkeleton *)luaL_checkudata(L, idx, MINI_SKELETON_MT);
}

static int skeleton_new(lua_State *L) {
	SceneObject **obj = lua_touserdata(L, 1);
	if (!obj || !*obj) return luaL_error(L, "invalid SceneObject");

	void *skeleton = get_skeleton_from_object(*obj);
	if (!skeleton) return luaL_error(L, "no skeleton");

	get_entry(skeleton);

	MiniSkeleton *ud = lua_newuserdata(L, sizeof(MiniSkeleton));
	ud->skeleton = skeleton;
	luaL_getmetatable(L, MINI_SKELETON_MT);
	lua_setmetatable(L, -2);
	return 1;
}

static int skeleton_set_offset(lua_State *L) {
	MiniSkeleton *self = check_skeleton(L, 1);
	int bone = (int)luaL_checkinteger(L, 2);
	BoneEntry *e = get_entry(self->skeleton);
	e->bones[bone].offX = (float)luaL_checknumber(L, 3);
	e->bones[bone].offY = (float)luaL_checknumber(L, 4);
	e->bones[bone].offZ = (float)luaL_checknumber(L, 5);
	lua_settop(L, 1);
	return 1;
}

static int skeleton_set_rotation(lua_State *L) {
	MiniSkeleton *self = check_skeleton(L, 1);
	int bone = (int)luaL_checkinteger(L, 2);
	BoneEntry *e = get_entry(self->skeleton);
	e->bones[bone].rotX = (float)luaL_checknumber(L, 3);
	e->bones[bone].rotY = (float)luaL_checknumber(L, 4);
	e->bones[bone].rotZ = (float)luaL_checknumber(L, 5);
	lua_settop(L, 1);
	return 1;
}

static int skeleton_set_scale(lua_State *L) {
	MiniSkeleton *self = check_skeleton(L, 1);
	int bone = (int)luaL_checkinteger(L, 2);
	BoneEntry *e = get_entry(self->skeleton);
	e->bones[bone].sX = (float)luaL_checknumber(L, 3);
	e->bones[bone].sY = (float)luaL_checknumber(L, 4);
	e->bones[bone].sZ = (float)luaL_checknumber(L, 5);
	lua_settop(L, 1);
	return 1;
}

static int skeleton_reset(lua_State *L) {
	MiniSkeleton *self = check_skeleton(L, 1);
	remove_entry(self->skeleton);
	lua_settop(L, 1);
	return 1;
}

static int skeleton_get_bone_index(lua_State *L) {
	MiniSkeleton *self = check_skeleton(L, 1);
	const char *name = luaL_checkstring(L, 2);
	if (!self->skeleton) return luaL_error(L, "null skeleton");

	/* core/stdstring.h does not seem to work */
	struct {
		size_t length;
		char pad[16];
		char data[256];
	} fake;

	size_t len = strlen(name);
	if (len >= sizeof(fake.data)) len = sizeof(fake.data) - 1;
	fake.length = len;
	memcpy(fake.data, name, len);
	fake.data[len] = '\0';

	char *dataPtr = fake.data;
	int index = BoneIndexForName(*$(void*, self->skeleton, 0x0, 0x0), &dataPtr);

	if (index < 0)
		return luaL_error(L, "bone '%s' not found", name);

	lua_pushinteger(L, index);
	return 1;
}

static const luaL_Reg skeleton_methods[] = {
	{"setBoneOffset",   skeleton_set_offset},
	{"setBoneRotation", skeleton_set_rotation},
	{"setBoneScale",    skeleton_set_scale},
	{"resetBones",      skeleton_reset},
	{"getBoneIndex",    skeleton_get_bone_index},
	{NULL, NULL}
};

static const luaL_Reg Skeleton[] = {
	{"New", skeleton_new},
	{NULL, NULL}
};

void API_register_skeleton(lua_State *L) {
	luaL_newmetatable(L, MINI_SKELETON_MT);
	lua_pushvalue(L, -1);
	lua_setfield(L, -2, "__index");
	for (int i = 0; skeleton_methods[i].name != NULL; i++) {
		lua_pushcfunction(L, skeleton_methods[i].func);
		lua_setfield(L, -2, skeleton_methods[i].name);
	}
	lua_pop(L, 1);

	lua_newtable(L);
	for (int i = 0; Skeleton[i].name != NULL; i++) {
		lua_pushcfunction(L, Skeleton[i].func);
		lua_setfield(L, -2, Skeleton[i].name);
	}
	lua_setglobal(L, "Skeleton");
}