#include "controller.h"

int NewTextInput(lua_State *L) {
	float nx = (float)luaL_optnumber(L, 1, 0.5);
	float ny = (float)luaL_optnumber(L, 2, 0.5);
	float nw = (float)luaL_optnumber(L, 3, 0.25);
	float nh = (float)luaL_optnumber(L, 4, 0.07);
	const char *hint = luaL_optstring(L, 5, "");

	MiniTextInput *ti = lua_newuserdata(L, sizeof(*ti));
	memset(ti, 0, sizeof(*ti));

	char *buf = malloc(32);
	snprintf(buf, 32, "txt_%p", (void *)ti);
	ti->id = buf;
	ti->nx = nx; ti->ny = ny;
	ti->nw = nw; ti->nh = nh;

	luaL_getmetatable(L, TEXTINPUT_MT);
	lua_setmetatable(L, -2);
	lua_newtable(L);
	lua_setfenv(L, -2);

	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("addTextInput",
	                          "(Ljava/lang/String;FFFFLjava/lang/String;)V");
	jstring jid = JID(env, ti);
	jstring jhint = (*env)->NewStringUTF(env, hint);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nx, ny, nw, nh, jhint);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jhint);

	LOGD("TextInput %s", buf);
	return 1;
}

static int ti_gc(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	if (ti->alwaysActive) return 0;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("removeTextInput", "(Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	free(ti->id);
	return 0;
}

static int ti_setAlwaysActive(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	ti->alwaysActive = lua_toboolean(L, 2);
	lua_settop(L, 1);
	return 1;
}

static int ti_setText(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	const char *text = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputText",
	                          "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	jstring jtext = (*env)->NewStringUTF(env, text);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jtext);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jtext);
	lua_settop(L, 1);
	return 1;
}

static int ti_getText(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls,
	                                        "getTextInputText", "(Ljava/lang/String;)Ljava/lang/String;");
	jstring jid = JID(env, ti);
	jstring res = (*env)->CallStaticObjectMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	if (!res) {
		lua_pushliteral(L, "");
		return 1;
	}
	const char *str = (*env)->GetStringUTFChars(env, res, NULL);
	lua_pushstring(L, str ? str : "");
	if (str) (*env)->ReleaseStringUTFChars(env, res, str);
	(*env)->DeleteLocalRef(env, res);
	return 1;
}

static int ti_setHint(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	const char *hint = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputHint",
	                          "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	jstring jhint = (*env)->NewStringUTF(env, hint);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jhint);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jhint);
	lua_settop(L, 1);
	return 1;
}

static int ti_setHidden(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputHidden", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_dismiss(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("dismissTextInput", "(Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setBackgroundResource(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	const char *res = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputBackgroundResource",
	                          "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	jstring jres = (*env)->NewStringUTF(env, res);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jres);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jres);
	lua_settop(L, 1);
	return 1;
}

static int ti_setFont(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	const char *font = luaL_checkstring(L, 2);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputFont",
	                          "(Ljava/lang/String;Ljava/lang/String;)V");
	jstring jid = JID(env, ti);
	jstring jfont = (*env)->NewStringUTF(env, font);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, jfont);
	(*env)->DeleteLocalRef(env, jid);
	(*env)->DeleteLocalRef(env, jfont);
	lua_settop(L, 1);
	return 1;
}

static int ti_isFocused(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls,
	                                        "isTextInputFocused", "(Ljava/lang/String;)Z");
	jstring jid = JID(env, ti);
	jboolean focused = (*env)->CallStaticBooleanMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	lua_pushboolean(L, focused);
	return 1;
}

static int ti_setSelection(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	int argc = lua_gettop(L);
	GET_ENV(); GET_CLS();
	jstring jid = JID(env, ti);
	if (argc == 2) {
		jmethodID m = VOID_METHOD("setTextInputSelection", "(Ljava/lang/String;I)V");
		(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	} else {
		jmethodID m = VOID_METHOD("setTextInputSelection", "(Ljava/lang/String;II)V");
		(*env)->CallStaticVoidMethod(env, cls, m, jid,
		                             (int)luaL_checkinteger(L, 2), (int)luaL_checkinteger(L, 3));
	}
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_getSelection(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = (*env)->GetStaticMethodID(env, cls,
	                                        "getTextInputSelection", "(Ljava/lang/String;)[I");
	jstring jid = JID(env, ti);
	jintArray arr = (*env)->CallStaticObjectMethod(env, cls, m, jid);
	(*env)->DeleteLocalRef(env, jid);
	if (!arr) {
		lua_pushinteger(L, 0);
		lua_pushinteger(L, 0);
		return 2;
	}
	jint *v = (*env)->GetIntArrayElements(env, arr, NULL);
	lua_pushinteger(L, v[0]);
	lua_pushinteger(L, v[1]);
	(*env)->ReleaseIntArrayElements(env, arr, v, 0);
	(*env)->DeleteLocalRef(env, arr);
	return 2;
}


static int ti_setBackgroundColor(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputBackgroundColor", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setTextColor(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputTextColor", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setTextSizeSp(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputTextSizeSp", "(Ljava/lang/String;F)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (float)luaL_checknumber(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setAlpha(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputAlpha", "(Ljava/lang/String;I)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (int)luaL_checkinteger(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setClickable(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputClickable", "(Ljava/lang/String;Z)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, (jboolean)lua_toboolean(L, 2));
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setPosition(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	float nx = (float)luaL_checknumber(L, 2);
	float ny = (float)luaL_checknumber(L, 3);
	ti->nx = nx; ti->ny = ny;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputPosition", "(Ljava/lang/String;FF)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nx, ny);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setDimensions(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	float nw = (float)luaL_checknumber(L, 2);
	float nh = (float)luaL_checknumber(L, 3);
	ti->nw = nw; ti->nh = nh;
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputDimensions", "(Ljava/lang/String;FF)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, nw, nh);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_setPadding(lua_State *L) {
	MiniTextInput *ti = CHECK_TEXTINPUT(L);
	int l = (int)luaL_checkinteger(L, 2);
	int t = (int)luaL_checkinteger(L, 3);
	int r = (int)luaL_checkinteger(L, 4);
	int b = (int)luaL_checkinteger(L, 5);
	GET_ENV(); GET_CLS();
	jmethodID m = VOID_METHOD("setTextInputPadding", "(Ljava/lang/String;IIII)V");
	jstring jid = JID(env, ti);
	(*env)->CallStaticVoidMethod(env, cls, m, jid, l, t, r, b);
	(*env)->DeleteLocalRef(env, jid);
	lua_settop(L, 1);
	return 1;
}

static int ti_index(lua_State *L) {
	lua_getfenv(L, 1);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	if (!lua_isnil(L, -1)) return 1;
	lua_pop(L, 2);
	luaL_getmetatable(L, TEXTINPUT_MT);
	lua_pushvalue(L, 2);
	lua_gettable(L, -2);
	return 1;
}

static const luaL_Reg textinput_methods[] = {
	{"__gc", ti_gc},
	{"setText", ti_setText},
	{"getText", ti_getText},
	{"setHint", ti_setHint},
	{"setHidden", ti_setHidden},
	{"setBackgroundResource", ti_setBackgroundResource},
	{"setBackgroundColor", ti_setBackgroundColor},
	{"setTextColor", ti_setTextColor},
	{"setTextSizeSp", ti_setTextSizeSp},
	{"setAlpha", ti_setAlpha},
	{"setClickable", ti_setClickable},
	{"setPosition", ti_setPosition},
	{"setDimensions", ti_setDimensions},
	{"setPadding", ti_setPadding},
	{"setFont", ti_setFont},
	{"isFocused", ti_isFocused},
	{"setSelection", ti_setSelection},
	{"getSelection", ti_getSelection},
	{"setAlwaysActive", ti_setAlwaysActive},
	{"dismiss", ti_dismiss},
	{NULL, NULL}
};

void open_textinput_mt(lua_State *L) {
	luaL_newmetatable(L, TEXTINPUT_MT);
	lua_pushcfunction(L, ti_index);
	lua_setfield(L, -2, "__index");
	lua_pushcfunction(L, mini_newindex);
	lua_setfield(L, -2, "__newindex");
	for (int i = 0; textinput_methods[i].name; i++) {
		lua_pushcfunction(L, textinput_methods[i].func);
		lua_setfield(L, -2, textinput_methods[i].name);
	}
	lua_pop(L, 1);
}