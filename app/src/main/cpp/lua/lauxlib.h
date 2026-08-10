/*
** lauxlib.h  (Kiwi build — dynamically resolved)
** Lua 5.1.5 auxiliary library, declared as function-pointer externs.
** Populated by init_lual() in lauxlib.c via swordigo_dlsym(), matching
** the mangled luaL_ names dumped from the target binary.
*/

#ifndef kiwi_lauxlib_h
#define kiwi_lauxlib_h

#include <stddef.h>
#include "lua.h"

/* ------------------------------------------------------------------ */
/* extra error code for loadfile                                       */
/* ------------------------------------------------------------------ */
#define LUA_ERRFILE (LUA_ERRERR + 1)

/* ------------------------------------------------------------------ */
/* luaL_Reg — array-of-{name,func} used to register libraries          */
/* ------------------------------------------------------------------ */
typedef struct luaL_Reg {
	const char *name;
	lua_CFunction func;
} luaL_Reg;

/* deprecated alias, kept for source compat with older Lua code */
#define luaL_reg luaL_Reg

/* ------------------------------------------------------------------ */
/* luaL_Buffer — incremental string-building buffer                    */
/* ------------------------------------------------------------------ */
#define LUAL_BUFFERSIZE 8192  /* mirrors BUFSIZ on most libc; safe upper bound */

typedef struct luaL_Buffer {
	char *p;                    /* current position in buffer */
	int lvl;                    /* number of strings in the stack (level) */
	lua_State *L;
	char buffer[LUAL_BUFFERSIZE];
} luaL_Buffer;

/* ------------------------------------------------------------------ */
/* reference system                                                     */
/* ------------------------------------------------------------------ */
#define LUA_NOREF  (-2)
#define LUA_REFNIL (-1)

/* ------------------------------------------------------------------ */
/* function-pointer API — populated by init_lual()                     */
/* ------------------------------------------------------------------ */

extern int   (*luaL_error)(lua_State *L, const char *fmt, ...);
extern void  (*luaL_unref)(lua_State *L, int t, int ref);
extern void  (*luaL_where)(lua_State *L, int lvl);
extern void  (*luaL_openlib)(lua_State *L, const char *libname, const luaL_Reg *l, int nup);
extern void  (*luaL_addvalue)(luaL_Buffer *B);
extern int   (*luaL_argerror)(lua_State *L, int numarg, const char *extramsg);
extern void  (*luaL_buffinit)(lua_State *L, luaL_Buffer *B);
extern int   (*luaL_callmeta)(lua_State *L, int obj, const char *e);
extern void  (*luaL_checkany)(lua_State *L, int narg);
extern int   (*luaL_loadfile)(lua_State *L, const char *filename);
extern lua_State *(*luaL_newstate)(void);
extern void  (*luaL_register)(lua_State *L, const char *libname, const luaL_Reg *l);
extern int   (*luaL_typerror)(lua_State *L, int narg, const char *tname);
extern void  (*luaL_addstring)(luaL_Buffer *B, const char *s);
extern void  (*luaL_checktype)(lua_State *L, int narg, int t);
extern const char *(*luaL_findtable)(lua_State *L, int idx, const char *fname, int szhint);
extern lua_Number (*luaL_optnumber)(lua_State *L, int narg, lua_Number d);
extern void  (*luaL_addlstring)(luaL_Buffer *B, const char *s, size_t l);
extern void  (*luaL_checkstack)(lua_State *L, int sz, const char *msg);
extern void *(*luaL_checkudata)(lua_State *L, int narg, const char *tname);
extern int   (*luaL_loadbuffer)(lua_State *L, const char *buff, size_t sz, const char *name);
extern int   (*luaL_loadstring)(lua_State *L, const char *s);
extern lua_Integer (*luaL_optinteger)(lua_State *L, int narg, lua_Integer d);
extern const char *(*luaL_optlstring)(lua_State *L, int narg, const char *d, size_t *len);
extern char *(*luaL_prepbuffer)(luaL_Buffer *B);
extern void  (*luaL_pushresult)(luaL_Buffer *B);
extern lua_Number (*luaL_checknumber)(lua_State *L, int narg);
extern int   (*luaL_checkoption)(lua_State *L, int narg, const char *def, const char *const lst[]);
extern lua_Integer (*luaL_checkinteger)(lua_State *L, int narg);
extern const char *(*luaL_checklstring)(lua_State *L, int narg, size_t *l);
extern int   (*luaL_getmetafield)(lua_State *L, int obj, const char *e);
extern int   (*luaL_newmetatable)(lua_State *L, const char *tname);
extern int   (*luaL_ref)(lua_State *L, int t);
extern const char *(*luaL_gsub)(lua_State *L, const char *s, const char *p, const char *r);

/* ------------------------------------------------------------------ */
/* convenience macros — identical to stock lauxlib.h                   */
/* ------------------------------------------------------------------ */

#define luaL_argcheck(L, cond,numarg,extramsg) \
	((void)((cond) || luaL_argerror(L, (numarg), (extramsg))))
#define luaL_checkstring(L,n)   (luaL_checklstring(L, (n), NULL))
#define luaL_optstring(L,n,d)   (luaL_optlstring(L, (n), (d), NULL))
#define luaL_checkint(L,n)      ((int)luaL_checkinteger(L, (n)))
#define luaL_checklong(L,n)     ((long)luaL_checkinteger(L, (n)))
#define luaL_optint(L,n,d)      ((int)luaL_optinteger(L, (n), (d)))
#define luaL_optlong(L,n,d)     ((long)luaL_optinteger(L, (n), (d)))

#define luaL_typename(L,i)      lua_typename(L, lua_type(L, (i)))

#define luaL_dofile(L, fn) \
	(luaL_loadfile(L, (fn)) || lua_pcall(L, 0, -1 /* LUA_MULTRET */, 0))
#define luaL_dostring(L, s) \
	(luaL_loadstring(L, (s)) || lua_pcall(L, 0, -1 /* LUA_MULTRET */, 0))

/* NOTE: unlike luaL_newmetatable (a real exported symbol above),
** luaL_getmetatable is a macro in stock Lua 5.1 — that's why it does
** not appear in the functions.c dump. */
#define luaL_getmetatable(L,n)  (lua_getfield(L, LUA_REGISTRYINDEX, (n)))

#define luaL_opt(L,f,n,d)       (lua_isnoneornil(L,(n)) ? (d) : f(L,(n)))

/* generic buffer manipulation, also macros in stock Lua */
#define luaL_addchar(B,c) \
	((void)((B)->p < ((B)->buffer + LUAL_BUFFERSIZE) || luaL_prepbuffer(B)), \
	 (*(B)->p++ = (char)(c)))
#define luaL_addsize(B,n)  ((B)->p += (n))
#define luaL_putchar(B,c)  luaL_addchar(B,c)

#endif /* kiwi_lauxlib_h */