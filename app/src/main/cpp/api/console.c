#include "lua.h"
#include "lauxlib.h"
#include "log.h"
#include "java.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "ConsoleAPI"

/* Fixed tag forced on normal (non-Swordiforge) mods */
#define NORMAL_MOD_TAG "libswordigo"

/* Default tag used by exclusives when none is supplied */
#define EXCLUSIVE_DEFAULT_TAG "Swordiforge"

#define MAX_TAG_LEN   64
#define MAX_MSG_LEN   4096

/* Table formatter limits */
#define TBL_MAX_ROWS     64
#define TBL_MAX_COLS     16
#define TBL_MAX_CELL     48
#define TBL_MAX_OUT      8192

/* ------------------------------------------------------------------ */
/* Swordiforge / exclusive detection                                   */
/* ------------------------------------------------------------------ */

static int is_swordiforge_exclusive(void) {
	const char *mod = java_current_mod_id();
	return mod && mod[0] && strstr(mod, "net.kiwi") != NULL;
}

/* ------------------------------------------------------------------ */
/* Core logging                                                        */
/* ------------------------------------------------------------------ */

static void console_emit(int prio, const char *level, const char *tag, const char *msg) {
	if (!tag || !tag[0])
		tag = is_swordiforge_exclusive() ? EXCLUSIVE_DEFAULT_TAG : NORMAL_MOD_TAG;
	if (!msg) msg = "";

	/* Multi-line messages: print each line so logcat stays readable */
	const char *p = msg;
	while (*p) {
		const char *nl = strchr(p, '\n');
		if (!nl) {
			__android_log_print(prio, tag, "%s", p);
			break;
		}
		char line[MAX_MSG_LEN];
		size_t n = (size_t)(nl - p);
		if (n >= sizeof(line)) n = sizeof(line) - 1;
		memcpy(line, p, n);
		line[n] = '\0';
		__android_log_print(prio, tag, "%s", line);
		p = nl + 1;
	}
}

static const char *lua_args_to_msg(lua_State *L, int from) {
	static char buf[MAX_MSG_LEN];
	size_t pos = 0;
	int top = lua_gettop(L);

	buf[0] = '\0';
	for (int i = from; i <= top; i++) {
		size_t len = 0;
		const char *s;

		if (lua_isnil(L, i)) {
			s = "nil";
			len = 3;
		} else if (lua_type(L, i) == LUA_TBOOLEAN) {
			s = lua_toboolean(L, i) ? "true" : "false";
			len = strlen(s);
		} else {
			s = lua_tolstring(L, i, &len);
			if (!s) {
				s = lua_typename(L, lua_type(L, i));
				len = strlen(s);
			}
		}

		if (pos > 0 && pos + 1 < sizeof(buf))
			buf[pos++] = ' ';
		if (pos + len >= sizeof(buf))
			len = sizeof(buf) - pos - 1;
		if (len > 0) {
			memcpy(buf + pos, s, len);
			pos += len;
		}
		buf[pos] = '\0';
	}
	return buf;
}

static int parse_tag(lua_State *L, char *tag_out, size_t tag_size) {
	tag_out[0] = '\0';

	if (!is_swordiforge_exclusive()) {
		snprintf(tag_out, tag_size, "%s", NORMAL_MOD_TAG);
		return 1;
	}

	if (lua_gettop(L) >= 2 && lua_type(L, 1) == LUA_TSTRING) {
		const char *t = lua_tostring(L, 1);
		if (t && t[0]) {
			snprintf(tag_out, tag_size, "%s", t);
			return 2;
		}
	}

	snprintf(tag_out, tag_size, "%s", EXCLUSIVE_DEFAULT_TAG);
	return 1;
}

/* ------------------------------------------------------------------ */
/* JS-style console.table formatter                                    */
/* ------------------------------------------------------------------ */

static void cell_set(char *dst, size_t n, const char *s) {
	if (!s) s = "";
	snprintf(dst, n, "%s", s);
	size_t len = strlen(dst);
	if (len >= n - 1 && n > 4) {
		dst[n - 4] = '.';
		dst[n - 3] = '.';
		dst[n - 2] = '.';
		dst[n - 1] = '\0';
	}
}

static void cell_from_lua(lua_State *L, int idx, char *dst, size_t n) {
	int t = lua_type(L, idx);
	char tmp[TBL_MAX_CELL];

	switch (t) {
		case LUA_TNIL:
			cell_set(dst, n, "undefined");
			break;
		case LUA_TBOOLEAN:
			cell_set(dst, n, lua_toboolean(L, idx) ? "true" : "false");
			break;
		case LUA_TNUMBER: {
			lua_Number num = lua_tonumber(L, idx);
			if (num == (lua_Number)(lua_Integer)num)
				snprintf(tmp, sizeof(tmp), "%d", (int)(lua_Integer)num);
			else
				snprintf(tmp, sizeof(tmp), "%g", (double)num);
			cell_set(dst, n, tmp);
			break;
		}
		case LUA_TSTRING:
			cell_set(dst, n, lua_tostring(L, idx));
			break;
		case LUA_TTABLE:
			cell_set(dst, n, "[Object]");
			break;
		case LUA_TFUNCTION:
			cell_set(dst, n, "[Function]");
			break;
		case LUA_TUSERDATA:
		case LUA_TLIGHTUSERDATA:
			cell_set(dst, n, "[Userdata]");
			break;
		case LUA_TTHREAD:
			cell_set(dst, n, "[Thread]");
			break;
		default:
			cell_set(dst, n, "[Unknown]");
			break;
	}
}

static int str_width(const char *s) {
	return (int)strlen(s ? s : "");
}

static int out_append(char *out, int pos, int cap, const char *s) {
	if (pos < 0) return pos;
	int len = (int)strlen(s);
	if (pos + len >= cap) {
		if (pos < cap - 1) {
			int n = cap - 1 - pos;
			memcpy(out + pos, s, (size_t)n);
			pos = cap - 1;
			out[pos] = '\0';
		}
		return -1;
	}
	memcpy(out + pos, s, (size_t)len);
	pos += len;
	out[pos] = '\0';
	return pos;
}

static int out_hline_seg(char *out, int pos, int cap, int width) {
	/* UTF-8 box drawing ─ (U+2500) = e2 94 80 */
	for (int k = 0; k < width; k++)
		pos = out_append(out, pos, cap, "\xe2\x94\x80");
	return pos;
}

/*
 * Draw:
 * ┌─────────┬────────┐
 * │ (index) │ Values │
 * ├─────────┼────────┤
 * │ a       │ 1      │
 * └─────────┴────────┘
 */
static int draw_table(char *out, int cap,
                      char headers[][TBL_MAX_CELL], int *widths, int ncols,
                      char cells[][TBL_MAX_COLS][TBL_MAX_CELL], int nrows) {
	int pos = 0;
	int i, c;

	for (c = 0; c < ncols; c++) {
		int w = str_width(headers[c]);
		if (w > widths[c]) widths[c] = w;
		for (i = 0; i < nrows; i++) {
			w = str_width(cells[i][c]);
			if (w > widths[c]) widths[c] = w;
		}
		if (widths[c] < 1) widths[c] = 1;
	}

	/* top: ┌──┬──┐ */
	pos = out_append(out, pos, cap, "\xe2\x94\x8c"); /* ┌ */
	for (c = 0; c < ncols; c++) {
		pos = out_hline_seg(out, pos, cap, widths[c] + 2);
		pos = out_append(out, pos, cap,
		                 c == ncols - 1 ? "\xe2\x94\x90" : "\xe2\x94\xac"); /* ┐ / ┬ */
	}
	pos = out_append(out, pos, cap, "\n");

	/* header */
	pos = out_append(out, pos, cap, "\xe2\x94\x82"); /* │ */
	for (c = 0; c < ncols; c++) {
		char cell[TBL_MAX_CELL + 8];
		snprintf(cell, sizeof(cell), " %-*s ", widths[c], headers[c]);
		pos = out_append(out, pos, cap, cell);
		pos = out_append(out, pos, cap, "\xe2\x94\x82");
	}
	pos = out_append(out, pos, cap, "\n");

	/* sep: ├──┼──┤ */
	pos = out_append(out, pos, cap, "\xe2\x94\x9c"); /* ├ */
	for (c = 0; c < ncols; c++) {
		pos = out_hline_seg(out, pos, cap, widths[c] + 2);
		pos = out_append(out, pos, cap,
		                 c == ncols - 1 ? "\xe2\x94\xa4" : "\xe2\x94\xbc"); /* ┤ / ┼ */
	}
	pos = out_append(out, pos, cap, "\n");

	/* body */
	for (i = 0; i < nrows; i++) {
		pos = out_append(out, pos, cap, "\xe2\x94\x82");
		for (c = 0; c < ncols; c++) {
			char cell[TBL_MAX_CELL + 8];
			snprintf(cell, sizeof(cell), " %-*s ", widths[c], cells[i][c]);
			pos = out_append(out, pos, cap, cell);
			pos = out_append(out, pos, cap, "\xe2\x94\x82");
		}
		pos = out_append(out, pos, cap, "\n");
	}

	/* bottom: └──┴──┘ */
	pos = out_append(out, pos, cap, "\xe2\x94\x94"); /* └ */
	for (c = 0; c < ncols; c++) {
		pos = out_hline_seg(out, pos, cap, widths[c] + 2);
		pos = out_append(out, pos, cap,
		                 c == ncols - 1 ? "\xe2\x94\x98" : "\xe2\x94\xb4"); /* ┘ / ┴ */
	}

	if (pos < 0)
		return (int)strlen(out);
	return pos;
}

/* Pure array: keys 1..n contiguous. Returns length or 0. */
static int table_array_len(lua_State *L, int idx) {
	if (idx < 0) idx = lua_gettop(L) + idx + 1;
	int n = (int)lua_objlen(L, idx);
	if (n <= 0) return 0;

	int count = 0;
	lua_pushnil(L);
	while (lua_next(L, idx) != 0) {
		count++;
		if (lua_type(L, -2) != LUA_TNUMBER) {
			lua_pop(L, 2);
			return 0;
		}
		lua_Number k = lua_tonumber(L, -2);
		if (k != (lua_Number)(int)k || (int)k < 1 || (int)k > n) {
			lua_pop(L, 2);
			return 0;
		}
		lua_pop(L, 1);
	}
	return (count == n) ? n : 0;
}

static int array_of_objects(lua_State *L, int idx, int n) {
	if (idx < 0) idx = lua_gettop(L) + idx + 1;
	for (int i = 1; i <= n; i++) {
		lua_rawgeti(L, idx, i);
		int isobj = lua_istable(L, -1);
		lua_pop(L, 1);
		if (!isobj) return 0;
	}
	return 1;
}

static int collect_object_columns(lua_State *L, int idx, int n,
                                  char cols[][TBL_MAX_CELL], int max_cols) {
	if (idx < 0) idx = lua_gettop(L) + idx + 1;
	int ncols = 0;

	for (int i = 1; i <= n && ncols < max_cols; i++) {
		lua_rawgeti(L, idx, i);
		if (!lua_istable(L, -1)) {
			lua_pop(L, 1);
			continue;
		}
		lua_pushnil(L);
		while (lua_next(L, -2) != 0 && ncols < max_cols) {
			if (lua_type(L, -2) == LUA_TSTRING) {
				const char *k = lua_tostring(L, -2);
				int found = 0;
				for (int c = 0; c < ncols; c++) {
					if (strcmp(cols[c], k) == 0) {
						found = 1;
						break;
					}
				}
				if (!found) {
					cell_set(cols[ncols], TBL_MAX_CELL, k);
					ncols++;
				}
			}
			lua_pop(L, 1);
		}
		lua_pop(L, 1);
	}
	return ncols;
}

static void format_console_table(lua_State *L, int idx, char *out, int cap) {
	out[0] = '\0';
	if (idx < 0) idx = lua_gettop(L) + idx + 1;

	if (!lua_istable(L, idx)) {
		char cell[TBL_MAX_CELL];
		cell_from_lua(L, idx, cell, sizeof(cell));
		char headers[1][TBL_MAX_CELL];
		char cells[1][TBL_MAX_COLS][TBL_MAX_CELL];
		int widths[1] = {0};
		cell_set(headers[0], TBL_MAX_CELL, "Value");
		cell_set(cells[0][0], TBL_MAX_CELL, cell);
		draw_table(out, cap, headers, widths, 1, cells, 1);
		return;
	}

	int arr_n = table_array_len(L, idx);

	char headers[TBL_MAX_COLS][TBL_MAX_CELL];
	char cells[TBL_MAX_ROWS][TBL_MAX_COLS][TBL_MAX_CELL];
	int widths[TBL_MAX_COLS];
	memset(widths, 0, sizeof(widths));
	int ncols = 0;
	int nrows = 0;

	if (arr_n > 0 && array_of_objects(L, idx, arr_n)) {
		/* array of objects → (index) | key1 | key2 | ... */
		char cols[TBL_MAX_COLS][TBL_MAX_CELL];
		int data_cols = collect_object_columns(L, idx, arr_n, cols, TBL_MAX_COLS - 1);

		cell_set(headers[0], TBL_MAX_CELL, "(index)");
		for (int c = 0; c < data_cols; c++)
			cell_set(headers[c + 1], TBL_MAX_CELL, cols[c]);
		ncols = data_cols + 1;

		nrows = arr_n < TBL_MAX_ROWS ? arr_n : TBL_MAX_ROWS;
		for (int i = 0; i < nrows; i++) {
			char idxbuf[16];
			snprintf(idxbuf, sizeof(idxbuf), "%d", i + 1);
			cell_set(cells[i][0], TBL_MAX_CELL, idxbuf);

			lua_rawgeti(L, idx, i + 1);
			for (int c = 0; c < data_cols; c++) {
				lua_getfield(L, -1, cols[c]);
				if (lua_isnil(L, -1))
					cell_set(cells[i][c + 1], TBL_MAX_CELL, "");
				else
					cell_from_lua(L, -1, cells[i][c + 1], TBL_MAX_CELL);
				lua_pop(L, 1);
			}
			lua_pop(L, 1);
		}
	} else if (arr_n > 0) {
		/* array of values → (index) | Values */
		cell_set(headers[0], TBL_MAX_CELL, "(index)");
		cell_set(headers[1], TBL_MAX_CELL, "Values");
		ncols = 2;
		nrows = arr_n < TBL_MAX_ROWS ? arr_n : TBL_MAX_ROWS;
		for (int i = 0; i < nrows; i++) {
			char idxbuf[16];
			snprintf(idxbuf, sizeof(idxbuf), "%d", i + 1);
			cell_set(cells[i][0], TBL_MAX_CELL, idxbuf);
			lua_rawgeti(L, idx, i + 1);
			cell_from_lua(L, -1, cells[i][1], TBL_MAX_CELL);
			lua_pop(L, 1);
		}
	} else {
		/* plain object → (index) | Values */
		cell_set(headers[0], TBL_MAX_CELL, "(index)");
		cell_set(headers[1], TBL_MAX_CELL, "Values");
		ncols = 2;
		nrows = 0;

		lua_pushnil(L);
		while (lua_next(L, idx) != 0 && nrows < TBL_MAX_ROWS) {
			if (lua_type(L, -2) == LUA_TSTRING)
				cell_set(cells[nrows][0], TBL_MAX_CELL, lua_tostring(L, -2));
			else
				cell_from_lua(L, -2, cells[nrows][0], TBL_MAX_CELL);
			cell_from_lua(L, -1, cells[nrows][1], TBL_MAX_CELL);
			nrows++;
			lua_pop(L, 1);
		}
	}

	if (nrows == 0) {
		cell_set(headers[0], TBL_MAX_CELL, "(index)");
		cell_set(headers[1], TBL_MAX_CELL, "Values");
		ncols = 2;
	}

	draw_table(out, cap, headers, widths, ncols, cells, nrows);
}

/* ------------------------------------------------------------------ */
/* Lua bindings                                                        */
/* ------------------------------------------------------------------ */

static int l_console_debug(lua_State *L) {
	char tag[MAX_TAG_LEN];
	int from = parse_tag(L, tag, sizeof(tag));
	console_emit(ANDROID_LOG_DEBUG, "DEBUG", tag, lua_args_to_msg(L, from));
	return 0;
}

static int l_console_info(lua_State *L) {
	char tag[MAX_TAG_LEN];
	int from = parse_tag(L, tag, sizeof(tag));
	console_emit(ANDROID_LOG_INFO, "INFO", tag, lua_args_to_msg(L, from));
	return 0;
}

static int l_console_warn(lua_State *L) {
	char tag[MAX_TAG_LEN];
	int from = parse_tag(L, tag, sizeof(tag));
	console_emit(ANDROID_LOG_WARN, "WARN", tag, lua_args_to_msg(L, from));
	return 0;
}

static int l_console_error(lua_State *L) {
	char tag[MAX_TAG_LEN];
	int from = parse_tag(L, tag, sizeof(tag));
	console_emit(ANDROID_LOG_ERROR, "ERROR", tag, lua_args_to_msg(L, from));
	return 0;
}

static int l_console_log(lua_State *L) {
	return l_console_info(L);
}

static int l_console_table(lua_State *L) {
	char tag[MAX_TAG_LEN];
	const char *use_tag;

	if (is_swordiforge_exclusive() && lua_gettop(L) >= 2 && lua_type(L, 2) == LUA_TSTRING) {
		const char *t = lua_tostring(L, 2);
		snprintf(tag, sizeof(tag), "%s", t && t[0] ? t : EXCLUSIVE_DEFAULT_TAG);
		use_tag = tag;
	} else if (is_swordiforge_exclusive()) {
		use_tag = EXCLUSIVE_DEFAULT_TAG;
	} else {
		use_tag = NORMAL_MOD_TAG;
	}

	static char formatted[TBL_MAX_OUT];
	if (lua_gettop(L) < 1)
		snprintf(formatted, sizeof(formatted), "(empty)");
	else
		format_console_table(L, 1, formatted, (int)sizeof(formatted));

	console_emit(ANDROID_LOG_DEBUG, "TABLE", use_tag, formatted);
	lua_pushstring(L, formatted);
	return 1;
}

static int l_console_is_exclusive(lua_State *L) {
	lua_pushboolean(L, is_swordiforge_exclusive());
	return 1;
}

static const luaL_Reg Console[] = {
	{"Debug",       l_console_debug},
	{"Info",        l_console_info},
	{"Log",         l_console_log},
	{"Warn",        l_console_warn},
	{"Error",       l_console_error},
	{"Table",       l_console_table},
	{"IsExclusive", l_console_is_exclusive},
	{NULL, NULL}
};

void API_register_console(lua_State *L) {
	lua_newtable(L);
	for (int i = 0; Console[i].name != NULL; i++) {
		lua_pushcfunction(L, Console[i].func);
		lua_setfield(L, -2, Console[i].name);
	}
	lua_setglobal(L, "Console");
	LOGD("Console API registered (exclusive=%d)", is_swordiforge_exclusive());
}
