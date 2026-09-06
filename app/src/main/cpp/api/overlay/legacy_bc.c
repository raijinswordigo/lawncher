#include "controller.h"

#define LEGACY_VOID_ID(mname, sig, ...) do { \
	GET_ENV(); GET_CLS(); \
	jmethodID m = VOID_METHOD(mname, sig); \
	jstring jid = (*env)->NewStringUTF(env, id); \
	(*env)->CallStaticVoidMethod(env, cls, m, jid, ##__VA_ARGS__); \
	(*env)->DeleteLocalRef(env, jid); \
} while (0)

static int legacy_New(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	const char *label = luaL_checkstring(L, 2);
	float x = (float)luaL_checknumber(L, 3);
	float y = (float)luaL_checknumber(L, 4);
	float w = (float)luaL_checknumber(L, 5);
	float h = (float)luaL_checknumber(L, 6);

	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("addButton", "(Ljava/lang/String;Ljava/lang/String;FFFF)V");
	jstring jid = (*env)->NewStringUTF(env, id);
	jstring jlabel = (*env)->NewStringUTF(env, label);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jlabel, x, y, w, h);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jlabel);
	return 0;
}

static int legacy_MakeMovable(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	jboolean snapback = (jboolean)lua_toboolean(L, 2);
	LEGACY_VOID_ID("makeMovable", "(Ljava/lang/String;Z)V", snapback);
	return 0;
}

static int legacy_SetClickable(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int clickable = lua_toboolean(L, 2);
	LEGACY_VOID_ID("setClickable", "(Ljava/lang/String;I)V", clickable);
	return 0;
}

static int legacy_SetHidden(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	jboolean hidden = (jboolean)lua_toboolean(L, 2);
	LEGACY_VOID_ID("setHidden", "(Ljava/lang/String;Z)V", hidden);
	return 0;
}

static int legacy_Delete(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	LEGACY_VOID_ID("removeButton", "(Ljava/lang/String;)V");
	return 0;
}

static int legacy_IsPressed(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	GET_ENV(); GET_CLS();
	jmethodID m = BOOL_METHOD("isPressed", "(Ljava/lang/String;)Z");
	jstring jid = (*env)->NewStringUTF(env, id);
	jboolean r = (*env)->CallStaticBooleanMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushboolean(L, r);
	return 1;
}

static int legacy_IsDragging(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	GET_ENV(); GET_CLS();
	jmethodID m = BOOL_METHOD("isDragging", "(Ljava/lang/String;)Z");
	jstring jid = (*env)->NewStringUTF(env, id);
	jboolean r = (*env)->CallStaticBooleanMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushboolean(L, r);
	return 1;
}

static int legacy_DeleteAll(lua_State *L) {
	(void)L;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeAll", "()V");
	if (m) (*env)->CallStaticVoidMethod(env, cls, m);
	return 0;
}

static int legacy_SetAlpha(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int alpha = (int)luaL_optinteger(L, 2, 255);
	LEGACY_VOID_ID("setAlpha", "(Ljava/lang/String;I)V", alpha);
	return 0;
}

static int legacy_SetScaling(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float sx = (float)luaL_checknumber(L, 2);
	float sy = (float)luaL_checknumber(L, 3);
	LEGACY_VOID_ID("setScaling", "(Ljava/lang/String;FF)V", sx, sy);
	return 0;
}

static int legacy_SetText(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	const char *text = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setText", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = (*env)->NewStringUTF(env, id);
	jstring jtext = (*env)->NewStringUTF(env, text);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jtext);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jtext);
	return 0;
}

static int legacy_SetTextScale(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float scale = (float)luaL_checknumber(L, 2);
	LEGACY_VOID_ID("setTextScale", "(Ljava/lang/String;F)V", scale);
	return 0;
}

static int legacy_SetTextColor(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int color = (int)luaL_checkinteger(L, 2);
	LEGACY_VOID_ID("setTextColor", "(Ljava/lang/String;I)V", color);
	return 0;
}

static int legacy_SetPadding(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int l = (int)luaL_checkinteger(L, 2);
	int t = (int)luaL_checkinteger(L, 3);
	int r = (int)luaL_checkinteger(L, 4);
	int b = (int)luaL_checkinteger(L, 5);
	LEGACY_VOID_ID("setPadding", "(Ljava/lang/String;IIII)V", l, t, r, b);
	return 0;
}

static int legacy_SetDimensions(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float w = (float)luaL_checknumber(L, 2);
	float h = (float)luaL_checknumber(L, 3);
	LEGACY_VOID_ID("setDimensions", "(Ljava/lang/String;FF)V", w, h);
	return 0;
}

static int legacy_SetAlignment(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int gravity = (int)luaL_checkinteger(L, 2);
	LEGACY_VOID_ID("setAlignment", "(Ljava/lang/String;I)V", gravity);
	return 0;
}

static int legacy_get_pos(lua_State *L, const char *id, float *ox, float *oy) {
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls, "getPosition", "(Ljava/lang/String;)[F");
	jstring jid = (*env)->NewStringUTF(env, id);
	jfloatArray arr = (*env)->CallStaticObjectMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	if (!arr) {
		*ox = 0.f;
		*oy = 0.f;
		return 0;
	}
	jfloat *v = (*env)->GetFloatArrayElements(env, arr, NULL);
	*ox = v[0];
	*oy = v[1];
	(*env)->ReleaseFloatArrayElements(env, arr, v, 0);
	(*env)->DeleteLocalRef(env, arr);
	return 1;
}

static int legacy_GetPositionX(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float x, y;
	legacy_get_pos(L, id, &x, &y);
	lua_pushnumber(L, x);
	return 1;
}

static int legacy_GetPositionY(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float x, y;
	legacy_get_pos(L, id, &x, &y);
	lua_pushnumber(L, y);
	return 1;
}

static int legacy_GetPosition(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float x, y;
	legacy_get_pos(L, id, &x, &y);
	lua_pushnumber(L, x);
	lua_pushnumber(L, y);
	return 2;
}

static int legacy_SetPosition(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	float x = (float)luaL_checknumber(L, 2);
	float y = (float)luaL_checknumber(L, 3);
	LEGACY_VOID_ID("setPosition", "(Ljava/lang/String;FF)V", x, y);
	return 0;
}

static int legacy_SetBackgroundResource(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	const char *res = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setBackgroundResource", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = (*env)->NewStringUTF(env, id);
	jstring jres = (*env)->NewStringUTF(env, res);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jres);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jres);
	return 0;
}

static int legacy_SetBackgroundAlpha(lua_State *L) {
	const char *id = luaL_checkstring(L, 1);
	int alpha = (int)luaL_optinteger(L, 2, 255);
	LEGACY_VOID_ID("setBackgroundAlpha", "(Ljava/lang/String;I)V", alpha);
	return 0;
}

static const luaL_Reg ButtonController[] = {
	{"New",                   legacy_New},
	{"MakeMovable",           legacy_MakeMovable},
	{"SetClickable",          legacy_SetClickable},
	{"SetHidden",             legacy_SetHidden},
	{"Delete",                legacy_Delete},
	{"IsPressed",             legacy_IsPressed},
	{"IsDragging",            legacy_IsDragging},
	{"DeleteAll",             legacy_DeleteAll},
	{"SetAlpha",              legacy_SetAlpha},
	{"SetScaling",            legacy_SetScaling},
	{"SetText",               legacy_SetText},
	{"SetTextScale",          legacy_SetTextScale},
	{"SetTextColor",          legacy_SetTextColor},
	{"SetPadding",            legacy_SetPadding},
	{"SetDimensions",         legacy_SetDimensions},
	{"SetAlignment",          legacy_SetAlignment},
	{"GetPositionX",          legacy_GetPositionX},
	{"GetPositionY",          legacy_GetPositionY},
	{"GetPosition",           legacy_GetPosition},
	{"SetPosition",           legacy_SetPosition},
	{"SetBackgroundResource", legacy_SetBackgroundResource},
	{"SetBackgroundAlpha",    legacy_SetBackgroundAlpha},
	{NULL, NULL}
};

void register_legacy_bc(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; ButtonController[i].name; i++) {
		lua_pushcfunction(L, ButtonController[i].func);
		lua_setfield(L, -2, ButtonController[i].name);
	}
	lua_setglobal(L, "ButtonController");
}