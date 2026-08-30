#include "controller.h"

int NewSeekBar(lua_State *L) {
	float nx = (float)luaL_optnumber(L, 1, 0.5);
	float ny = (float)luaL_optnumber(L, 2, 0.5);
	float nw = (float)luaL_optnumber(L, 3, 0.3);
	float nh = (float)luaL_optnumber(L, 4, 0.05);
	int min = (int)luaL_optinteger(L, 5, 0);
	int max = (int)luaL_optinteger(L, 6, 100);
	int progress = (int)luaL_optinteger(L, 7, min);

	MiniSeekBar *sb = lua_newuserdata(L, sizeof(*sb));
	memset(sb, 0, sizeof(*sb));

	char *buf = malloc(32);
	snprintf(buf, 32, "seek_%p", (void *)sb);
	sb->id = buf;

	luaL_getmetatable(L, SEEKBAR_MT);
	lua_setmetatable(L, -2);
	lua_newtable(L);
	lua_setfenv(L, -2);

	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("addSeekBar", "(Ljava/lang/String;FFFFIII)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nx, ny, nw, nh, min, max, progress);
	(*env)->DeleteLocalRef(env, jid);

	LOGD("SeekBar %s", buf);
	return 1;
}

static int sb_gc(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	if (sb->alwaysActive) return 0;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeSeekBar", "(Ljava/lang/String;)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	free(sb->id);
	return 0;
}

static int sb_setAlwaysActive(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	sb->alwaysActive = lua_toboolean(L, 2);
	lua_settop(L, 1);
	return 1;
}

static int sb_setProgress(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setSeekBarProgress", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int sb_getProgress(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = INT_METHOD("getSeekBarProgress", "(Ljava/lang/String;)I");
	jstring jid = JID(env, sb);
	jint value = (*env)->CallStaticIntMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushinteger(L, value);
	return 1;
}

static int sb_setMax(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setSeekBarMax", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int sb_getMax(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = INT_METHOD("getSeekBarMax", "(Ljava/lang/String;)I");
	jstring jid = JID(env, sb);
	jint value = (*env)->CallStaticIntMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushinteger(L, value);
	return 1;
}

static int sb_isTouching(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = BOOL_METHOD("isSeekBarTouching", "(Ljava/lang/String;)Z");
	jstring jid = JID(env, sb);
	jboolean touching = (*env)->CallStaticBooleanMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushboolean(L, touching);
	return 1;
}

static int sb_setHidden(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setSeekBarHidden", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int sb_setClickable(lua_State *L) {
	MiniSeekBar *sb = CHECK_SEEKBAR(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setSeekBarClickable", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, sb);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int sb_index(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	if (!lua_isnil(L, -1)) return 1;
	lua_pop(L, 2);
	luaL_getmetatable(L, SEEKBAR_MT);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	return 1;
}

static const luaL_Reg seekbar_methods[] = {
	{"__gc", sb_gc},
	{"setAlwaysActive", sb_setAlwaysActive},
	{"setProgress", sb_setProgress},
	{"getProgress", sb_getProgress},
	{"setMax", sb_setMax},
	{"getMax", sb_getMax},
	{"isTouching", sb_isTouching},
	{"setHidden", sb_setHidden},
	{"setClickable", sb_setClickable},
	{NULL, NULL}
};

void open_seekbar_mt(lua_State *L) {
	luaL_newmetatable(L, SEEKBAR_MT);
	lua_pushcfunction(L, sb_index);
	lua_setfield(L, -2, "__index");
	lua_pushcfunction(L, mini_newindex);
	lua_setfield(L, -2, "__newindex");
	for (int i = 0; seekbar_methods[i].name; i++) {
		lua_pushcfunction(L, seekbar_methods[i].func);
		lua_setfield(L, -2, seekbar_methods[i].name);
	}
	lua_pop(L, 1);
}