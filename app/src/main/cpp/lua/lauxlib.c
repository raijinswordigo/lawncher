#include "lauxlib.h"
#include "../core/hook.h"

/* ------------------------------------------------------------------ */
/* storage for every function pointer declared in lauxlib.h            */
/* ------------------------------------------------------------------ */

int   (*luaL_error)(lua_State *L, const char *fmt, ...);
void  (*luaL_unref)(lua_State *L, int t, int ref);
void  (*luaL_where)(lua_State *L, int lvl);
void  (*luaL_openlib)(lua_State *L, const char *libname, const luaL_Reg *l, int nup);
void  (*luaL_addvalue)(luaL_Buffer *B);
int   (*luaL_argerror)(lua_State *L, int numarg, const char *extramsg);
void  (*luaL_buffinit)(lua_State *L, luaL_Buffer *B);
int   (*luaL_callmeta)(lua_State *L, int obj, const char *e);
void  (*luaL_checkany)(lua_State *L, int narg);
int   (*luaL_loadfile)(lua_State *L, const char *filename);
lua_State *(*luaL_newstate)(void);
void  (*luaL_register)(lua_State *L, const char *libname, const luaL_Reg *l);
int   (*luaL_typerror)(lua_State *L, int narg, const char *tname);
void  (*luaL_addstring)(luaL_Buffer *B, const char *s);
void  (*luaL_checktype)(lua_State *L, int narg, int t);
const char *(*luaL_findtable)(lua_State *L, int idx, const char *fname, int szhint);
lua_Number (*luaL_optnumber)(lua_State *L, int narg, lua_Number d);
void  (*luaL_addlstring)(luaL_Buffer *B, const char *s, size_t l);
void  (*luaL_checkstack)(lua_State *L, int sz, const char *msg);
void *(*luaL_checkudata)(lua_State *L, int narg, const char *tname);
int   (*luaL_loadbuffer)(lua_State *L, const char *buff, size_t sz, const char *name);
int   (*luaL_loadstring)(lua_State *L, const char *s);
lua_Integer (*luaL_optinteger)(lua_State *L, int narg, lua_Integer d);
const char *(*luaL_optlstring)(lua_State *L, int narg, const char *d, size_t *len);
char *(*luaL_prepbuffer)(luaL_Buffer *B);
void  (*luaL_pushresult)(luaL_Buffer *B);
lua_Number (*luaL_checknumber)(lua_State *L, int narg);
int   (*luaL_checkoption)(lua_State *L, int narg, const char *def, const char *const lst[]);
lua_Integer (*luaL_checkinteger)(lua_State *L, int narg);
const char *(*luaL_checklstring)(lua_State *L, int narg, size_t *l);
int   (*luaL_getmetafield)(lua_State *L, int obj, const char *e);
int   (*luaL_newmetatable)(lua_State *L, const char *tname);
int   (*luaL_ref)(lua_State *L, int t);
const char *(*luaL_gsub)(lua_State *L, const char *s, const char *p, const char *r);

/* ------------------------------------------------------------------ */
/* init_lual — resolve every symbol against the mangled names dumped   */
/* from functions.c (grep ^luaL_)                                      */
/* ------------------------------------------------------------------ */
void init_lual() {
	luaL_error         = swordigo_dlsym("_Z10luaL_errorP9lua_StatePKcz");
	luaL_unref         = swordigo_dlsym("_Z10luaL_unrefP9lua_Stateii");
	luaL_where         = swordigo_dlsym("_Z10luaL_whereP9lua_Statei");
	luaL_openlib       = swordigo_dlsym("_Z12luaL_openlibP9lua_StatePKcPK8luaL_Regi");
	luaL_addvalue      = swordigo_dlsym("_Z13luaL_addvalueP11luaL_Buffer");
	luaL_argerror      = swordigo_dlsym("_Z13luaL_argerrorP9lua_StateiPKc");
	luaL_buffinit      = swordigo_dlsym("_Z13luaL_buffinitP9lua_StateP11luaL_Buffer");
	luaL_callmeta      = swordigo_dlsym("_Z13luaL_callmetaP9lua_StateiPKc");
	luaL_checkany      = swordigo_dlsym("_Z13luaL_checkanyP9lua_Statei");
	luaL_loadfile      = swordigo_dlsym("_Z13luaL_loadfileP9lua_StatePKc");
	luaL_newstate      = swordigo_dlsym("_Z13luaL_newstatev");
	luaL_register      = swordigo_dlsym("_Z13luaL_registerP9lua_StatePKcPK8luaL_Reg");
	luaL_typerror      = swordigo_dlsym("_Z13luaL_typerrorP9lua_StateiPKc");
	luaL_addstring     = swordigo_dlsym("_Z14luaL_addstringP11luaL_BufferPKc");
	luaL_checktype     = swordigo_dlsym("_Z14luaL_checktypeP9lua_Stateii");
	luaL_findtable     = swordigo_dlsym("_Z14luaL_findtableP9lua_StateiPKci");
	luaL_optnumber     = swordigo_dlsym("_Z14luaL_optnumberP9lua_Stateid");
	luaL_addlstring    = swordigo_dlsym("_Z15luaL_addlstringP11luaL_BufferPKcm");
	luaL_checkstack    = swordigo_dlsym("_Z15luaL_checkstackP9lua_StateiPKc");
	luaL_checkudata    = swordigo_dlsym("_Z15luaL_checkudataP9lua_StateiPKc");
	luaL_loadbuffer    = swordigo_dlsym("_Z15luaL_loadbufferP9lua_StatePKcmS2_");
	luaL_loadstring    = swordigo_dlsym("_Z15luaL_loadstringP9lua_StatePKc");
	luaL_optinteger    = swordigo_dlsym("_Z15luaL_optintegerP9lua_Stateil");
	luaL_optlstring    = swordigo_dlsym("_Z15luaL_optlstringP9lua_StateiPKcPm");
	luaL_prepbuffer    = swordigo_dlsym("_Z15luaL_prepbufferP11luaL_Buffer");
	luaL_pushresult    = swordigo_dlsym("_Z15luaL_pushresultP11luaL_Buffer");
	luaL_checknumber   = swordigo_dlsym("_Z16luaL_checknumberP9lua_Statei");
	luaL_checkoption   = swordigo_dlsym("_Z16luaL_checkoptionP9lua_StateiPKcPKS2_");
	luaL_checkinteger  = swordigo_dlsym("_Z17luaL_checkintegerP9lua_Statei");
	luaL_checklstring  = swordigo_dlsym("_Z17luaL_checklstringP9lua_StateiPm");
	luaL_getmetafield  = swordigo_dlsym("_Z17luaL_getmetafieldP9lua_StateiPKc");
	luaL_newmetatable  = swordigo_dlsym("_Z17luaL_newmetatableP9lua_StatePKc");
	luaL_ref           = swordigo_dlsym("_Z8luaL_refP9lua_Statei");
	luaL_gsub          = swordigo_dlsym("_Z9luaL_gsubP9lua_StatePKcS2_S2_");
}