#include "controller.h"

int NewOverlay(lua_State *L) {
	float nx = (float)luaL_optnumber(L, 1, 0.5);
	float ny = (float)luaL_optnumber(L, 2, 0.5);
	float nw = (float)luaL_optnumber(L, 3, 0.3);
	float nh = (float)luaL_optnumber(L, 4, 0.4);

	MiniOverlay *ov = lua_newuserdata(L, sizeof(*ov));
	memset(ov, 0, sizeof(*ov));

	char *buf = malloc(32);
	snprintf(buf, 32, "ov_%p", (void *)ov);
	ov->id = buf;
	ov->nx = nx; ov->ny = ny;
	ov->nw = nw; ov->nh = nh;

	luaL_getmetatable(L, OVERLAY_MT);
	lua_setmetatable(L, -2);
	lua_newtable(L);
	lua_setfenv(L, -2);

	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("newOverlay", "(Ljava/lang/String;FFFF)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nx, ny, nw, nh);
	(*env)->DeleteLocalRef(env, jid);

	LOGD("Overlay %s", buf);
	return 1;
}

static int ov_gc(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	if (ov->alwaysActive) return 0;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeOverlay", "(Ljava/lang/String;)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	free(ov->id);
	return 0;
}

static int ov_addButton(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	MiniButton *btn = (MiniButton *)luaL_checkudata(L, 2, BUTTON_MT);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("overlayAddButton", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jovid = JID(env, ov);
	jstring jbid = (*env)->NewStringUTF(env, btn->id);
	(*env)->CallStaticVoidMethod(env, cls, m, jovid, jbid);
	(*env)->DeleteLocalRef(env, jovid);
	(*env)->DeleteLocalRef(env, jbid);
	lua_pushvalue(L, 2);
	return 1;
}

static int ov_addSeekBar(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	MiniSeekBar *sb = (MiniSeekBar *)luaL_checkudata(L, 2, SEEKBAR_MT);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("overlayAddSeekBar", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jovid = JID(env, ov);
	jstring jsbid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jovid, jsbid);
	(*env)->DeleteLocalRef(env, jovid);
	(*env)->DeleteLocalRef(env, jsbid);
	lua_settop(L, 1);
	return 1;
}

static int ov_addDrawer(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	MiniDrawer *drw = (MiniDrawer *)luaL_checkudata(L, 2, DRAWER_MT);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("overlayAddDrawer", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jovid = JID(env, ov);
	jstring jdid = (*env)->NewStringUTF(env, drw->id);
	(*env)->CallStaticVoidMethod(env, cls, m, jovid, jdid);
	(*env)->DeleteLocalRef(env, jovid);
	(*env)->DeleteLocalRef(env, jdid);
	lua_pushvalue(L, 1);
	return 1;
}

static int ov_addTextInput(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	MiniTextInput *ti = (MiniTextInput *)luaL_checkudata(L, 2, TEXTINPUT_MT);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("overlayAddTextInput", "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jovid = JID(env, ov);
	jstring jtid = (*env)->NewStringUTF(env, ti->id);
	(*env)->CallStaticVoidMethod(env, cls, m, jovid, jtid);
	(*env)->DeleteLocalRef(env, jovid);
	(*env)->DeleteLocalRef(env, jtid);
	lua_pushvalue(L, 2);
	return 1;
}

static int ov_makeMovable(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayMovable", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_makePinchable(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayPinchable", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_setHidden(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayHidden", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_separator(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	float ny = (float)luaL_checknumber(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("overlayAddSeparator", "(Ljava/lang/String;F)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, ny);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_setPosition(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	float nx = (float)luaL_checknumber(L, 2);
	float ny = (float)luaL_checknumber(L, 3);
	ov->nx = nx; ov->ny = ny;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayPosition", "(Ljava/lang/String;FF)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nx, ny);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_getPosition(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls, "getOverlayPosition", "(Ljava/lang/String;)[F");
	jstring jid = JID(env, ov);
	jfloatArray arr = (*env)->CallStaticObjectMethod(env, cls, m, jid);
	jfloat *v = (*env)->GetFloatArrayElements(env, arr, NULL);
	lua_pushnumber(L, v[0]);
	lua_pushnumber(L, v[1]);
	(*env)->ReleaseFloatArrayElements(env, arr, v, 0);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, arr);
	return 2;
}

static int ov_remove(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeOverlay", "(Ljava/lang/String;)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	return 0;
}

static int ov_setBackgroundColor(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayBackgroundColor", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_setBackgroundAlpha(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayBackgroundAlpha", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_setCornerRadius(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayCornerRadius", "(Ljava/lang/String;F)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (float)luaL_checknumber(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_setDimensions(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	float nw = (float)luaL_checknumber(L, 2);
	float nh = (float)luaL_checknumber(L, 3);
	ov->nw = nw; ov->nh = nh;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setOverlayDimensions", "(Ljava/lang/String;FF)V");
	jstring jid = JID(env, ov);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nw, nh);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ov_getScaleFactor(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls, "getOverlayScaleFactor", "(Ljava/lang/String;)F");
	jstring jid = JID(env, ov);
	jfloat r = (*env)->CallStaticFloatMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushnumber(L, r);
	return 1;
}

static int ov_isPinching(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls, "isOverlayPinching", "(Ljava/lang/String;)Z");
	jstring jid = JID(env, ov);
	jboolean r = (*env)->CallStaticBooleanMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushboolean(L, r);
	return 1;
}

static int ov_setAlwaysActive(lua_State *L) {
	MiniOverlay *ov = CHECK_OVERLAY(L);
	ov->alwaysActive = lua_toboolean(L, 2);
	lua_settop(L, 1);
	return 1;
}

static int ov_index(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	if (!lua_isnil(L, -1)) return 1;
	lua_pop(L, 2);
	luaL_getmetatable(L, OVERLAY_MT);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	return 1;
}

static const luaL_Reg overlay_methods[] = {
	{"__gc", ov_gc},
	{"addButton", ov_addButton},
	{"addDrawer", ov_addDrawer},
	{"addSeekBar", ov_addSeekBar},
	{"addTextInput", ov_addTextInput},
	{"separator", ov_separator},
	{"setHidden", ov_setHidden},
	{"setPosition", ov_setPosition},
	{"getPosition", ov_getPosition},
	{"setBackgroundColor", ov_setBackgroundColor},
	{"setBackgroundAlpha", ov_setBackgroundAlpha},
	{"setCornerRadius", ov_setCornerRadius},
	{"setDimensions", ov_setDimensions},
	{"makeMovable", ov_makeMovable},
	{"makePinchable", ov_makePinchable},
	{"getPinchFactor", ov_getScaleFactor},
	{"isPinching", ov_isPinching},
	{"setAlwaysActive", ov_setAlwaysActive},
	{"remove", ov_remove},
	{NULL, NULL}
};

void open_overlay_mt(lua_State *L) {
	luaL_newmetatable(L, OVERLAY_MT);
	lua_pushcfunction(L, ov_index);
	lua_setfield(L, -2, "__index");
	lua_pushcfunction(L, mini_newindex);
	lua_setfield(L, -2, "__newindex");
	for (int i = 0; overlay_methods[i].name; i++) {
		lua_pushcfunction(L, overlay_methods[i].func);
		lua_setfield(L, -2, overlay_methods[i].name);
	}
	lua_pop(L, 1);
}