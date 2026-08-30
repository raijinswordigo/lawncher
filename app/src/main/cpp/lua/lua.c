#include "lua.h"
#include "../core/hook.h"

lua_State     *(*lua_newstate)(lua_Alloc f, void *ud);
void            (*lua_close)(lua_State *L);
lua_State     *(*lua_newthread)(lua_State *L);
lua_CFunction   (*lua_atpanic)(lua_State *L, lua_CFunction panicf);

int   (*lua_gettop)(lua_State *L);
void  (*lua_settop)(lua_State *L, int idx);
void  (*lua_pushvalue)(lua_State *L, int idx);
void  (*lua_remove)(lua_State *L, int idx);
void  (*lua_insert)(lua_State *L, int idx);
void  (*lua_replace)(lua_State *L, int idx);
int   (*lua_checkstack)(lua_State *L, int sz);
void  (*lua_xmove)(lua_State *from, lua_State *to, int n);

int          (*lua_isnumber)(lua_State *L, int idx);
int          (*lua_isstring)(lua_State *L, int idx);
int          (*lua_iscfunction)(lua_State *L, int idx);
int          (*lua_isuserdata)(lua_State *L, int idx);
int          (*lua_type)(lua_State *L, int idx);
const char  *(*lua_typename)(lua_State *L, int tp);

int          (*lua_equal)(lua_State *L, int idx1, int idx2);
int          (*lua_rawequal)(lua_State *L, int idx1, int idx2);
int          (*lua_lessthan)(lua_State *L, int idx1, int idx2);

lua_Number    (*lua_tonumber)(lua_State *L, int idx);
lua_Integer   (*lua_tointeger)(lua_State *L, int idx);
int           (*lua_toboolean)(lua_State *L, int idx);
const char   *(*lua_tolstring)(lua_State *L, int idx, size_t *len);
size_t        (*lua_objlen)(lua_State *L, int idx);
lua_CFunction (*lua_tocfunction)(lua_State *L, int idx);
void          *(*lua_touserdata)(lua_State *L, int idx);
lua_State    *(*lua_tothread)(lua_State *L, int idx);
const void   *(*lua_topointer)(lua_State *L, int idx);

void  (*lua_pushnil)(lua_State *L);
void  (*lua_pushnumber)(lua_State *L, lua_Number n);
void  (*lua_pushinteger)(lua_State *L, lua_Integer n);
void  (*lua_pushlstring)(lua_State *L, const char *s, size_t l);
void  (*lua_pushstring)(lua_State *L, const char *s);
const char *(*lua_pushvfstring)(lua_State *L, const char *fmt, va_list argp);
const char *(*lua_pushfstring)(lua_State *L, const char *fmt, ...);
void  (*lua_pushcclosure)(lua_State *L, lua_CFunction fn, int n);
void  (*lua_pushboolean)(lua_State *L, int b);
void  (*lua_pushlightuserdata)(lua_State *L, void *p);
int   (*lua_pushthread)(lua_State *L);

void  (*lua_gettable)(lua_State *L, int idx);
void  (*lua_getfield)(lua_State *L, int idx, const char *k);
void  (*lua_rawget)(lua_State *L, int idx);
void  (*lua_rawgeti)(lua_State *L, int idx, int n);
void  (*lua_createtable)(lua_State *L, int narr, int nrec);
void *(*lua_newuserdata)(lua_State *L, size_t sz);
int   (*lua_getmetatable)(lua_State *L, int objindex);
void  (*lua_getfenv)(lua_State *L, int idx);

void  (*lua_settable)(lua_State *L, int idx);
void  (*lua_setfield)(lua_State *L, int idx, const char *k);
void  (*lua_rawset)(lua_State *L, int idx);
void  (*lua_rawseti)(lua_State *L, int idx, int n);
int   (*lua_setmetatable)(lua_State *L, int objindex);
int   (*lua_setfenv)(lua_State *L, int idx);

void  (*lua_call)(lua_State *L, int nargs, int nresults);
int   (*lua_pcall)(lua_State *L, int nargs, int nresults, int errfunc);
int   (*lua_cpcall)(lua_State *L, lua_CFunction func, void *ud);
int   (*lua_load)(lua_State *L, lua_Reader reader, void *dt, const char *chunkname);
int   (*lua_dump)(lua_State *L, lua_Writer writer, void *data);

int   (*lua_yield)(lua_State *L, int nresults);
int   (*lua_resume)(lua_State *L, int narg);
int   (*lua_status)(lua_State *L);

int   (*lua_gc)(lua_State *L, int what, int data);

int   (*lua_error)(lua_State *L);
int   (*lua_next)(lua_State *L, int idx);
void  (*lua_concat)(lua_State *L, int n);
lua_Alloc (*lua_getallocf)(lua_State *L, void **ud);
void  (*lua_setallocf)(lua_State *L, lua_Alloc f, void *ud);

void  (*lua_setlevel)(lua_State *from, lua_State *to);
int   (*lua_getstack)(lua_State *L, int level, lua_Debug *ar);
int   (*lua_getinfo)(lua_State *L, const char *what, lua_Debug *ar);
const char *(*lua_getlocal)(lua_State *L, const lua_Debug *ar, int n);
const char *(*lua_setlocal)(lua_State *L, const lua_Debug *ar, int n);
const char *(*lua_getupvalue)(lua_State *L, int funcindex, int n);
const char *(*lua_setupvalue)(lua_State *L, int funcindex, int n);
int   (*lua_sethook)(lua_State *L, lua_Hook func, int mask, int count);
lua_Hook (*lua_gethook)(lua_State *L);
int   (*lua_gethookmask)(lua_State *L);
int   (*lua_gethookcount)(lua_State *L);

/* ------------------------------------------------------------------ */
/* init_lua — resolve every symbol against the mangled names dumped    */
/* from functions.c (grep ^lua_)                                       */
/* ------------------------------------------------------------------ */
void init_lua() {
	lua_newstate           = swordigo_dlsym("_Z12lua_newstatePFPvS_S_mmES_");
	lua_close              = swordigo_dlsym("_Z9lua_closeP9lua_State");
	lua_newthread          = swordigo_dlsym("_Z13lua_newthreadP9lua_State");
	lua_atpanic            = swordigo_dlsym("_Z11lua_atpanicP9lua_StatePFiS0_E");

	lua_gettop             = swordigo_dlsym("_Z10lua_gettopP9lua_State");
	lua_settop             = swordigo_dlsym("_Z10lua_settopP9lua_Statei");
	lua_pushvalue          = swordigo_dlsym("_Z13lua_pushvalueP9lua_Statei");
	lua_remove             = swordigo_dlsym("_Z10lua_removeP9lua_Statei");
	lua_insert             = swordigo_dlsym("_Z10lua_insertP9lua_Statei");
	lua_replace            = swordigo_dlsym("_Z11lua_replaceP9lua_Statei");
	lua_checkstack         = swordigo_dlsym("_Z14lua_checkstackP9lua_Statei");
	lua_xmove              = swordigo_dlsym("_Z9lua_xmoveP9lua_StateS0_i");

	lua_isnumber           = swordigo_dlsym("_Z12lua_isnumberP9lua_Statei");
	lua_isstring           = swordigo_dlsym("_Z12lua_isstringP9lua_Statei");
	lua_iscfunction        = swordigo_dlsym("_Z15lua_iscfunctionP9lua_Statei");
	lua_isuserdata         = swordigo_dlsym("_Z14lua_isuserdataP9lua_Statei");
	lua_type               = swordigo_dlsym("_Z8lua_typeP9lua_Statei");
	lua_typename           = swordigo_dlsym("_Z12lua_typenameP9lua_Statei");

	lua_equal              = swordigo_dlsym("_Z9lua_equalP9lua_Stateii");
	lua_rawequal           = swordigo_dlsym("_Z12lua_rawequalP9lua_Stateii");
	lua_lessthan           = swordigo_dlsym("_Z12lua_lessthanP9lua_Stateii");

	lua_tonumber           = swordigo_dlsym("_Z12lua_tonumberP9lua_Statei");
	lua_tointeger          = swordigo_dlsym("_Z13lua_tointegerP9lua_Statei");
	lua_toboolean          = swordigo_dlsym("_Z13lua_tobooleanP9lua_Statei");
	lua_tolstring          = swordigo_dlsym("_Z13lua_tolstringP9lua_StateiPm");
	lua_objlen             = swordigo_dlsym("_Z10lua_objlenP9lua_Statei");
	lua_tocfunction        = swordigo_dlsym("_Z15lua_tocfunctionP9lua_Statei");
	lua_touserdata         = swordigo_dlsym("_Z14lua_touserdataP9lua_Statei");
	lua_tothread           = swordigo_dlsym("_Z12lua_tothreadP9lua_Statei");
	lua_topointer           = swordigo_dlsym("_Z13lua_topointerP9lua_Statei");

	lua_pushnil            = swordigo_dlsym("_Z11lua_pushnilP9lua_State");
	lua_pushnumber         = swordigo_dlsym("_Z14lua_pushnumberP9lua_Stated");
	lua_pushinteger        = swordigo_dlsym("_Z15lua_pushintegerP9lua_Statel");
	lua_pushlstring        = swordigo_dlsym("_Z15lua_pushlstringP9lua_StatePKcm");
	lua_pushstring         = swordigo_dlsym("_Z14lua_pushstringP9lua_StatePKc");
	lua_pushvfstring       = swordigo_dlsym("_Z16lua_pushvfstringP9lua_StatePKcSt9__va_list");
	lua_pushfstring        = swordigo_dlsym("_Z15lua_pushfstringP9lua_StatePKcz");
	lua_pushcclosure       = swordigo_dlsym("_Z16lua_pushcclosureP9lua_StatePFiS0_Ei");
	lua_pushboolean        = swordigo_dlsym("_Z15lua_pushbooleanP9lua_Statei");
	lua_pushlightuserdata  = swordigo_dlsym("_Z21lua_pushlightuserdataP9lua_StatePv");
	lua_pushthread         = swordigo_dlsym("_Z14lua_pushthreadP9lua_State");

	lua_gettable           = swordigo_dlsym("_Z12lua_gettableP9lua_Statei");
	lua_getfield           = swordigo_dlsym("_Z12lua_getfieldP9lua_StateiPKc");
	lua_rawget             = swordigo_dlsym("_Z10lua_rawgetP9lua_Statei");
	lua_rawgeti            = swordigo_dlsym("_Z11lua_rawgetiP9lua_Stateii");
	lua_createtable        = swordigo_dlsym("_Z15lua_createtableP9lua_Stateii");
	lua_newuserdata        = swordigo_dlsym("_Z15lua_newuserdataP9lua_Statem");
	lua_getmetatable       = swordigo_dlsym("_Z16lua_getmetatableP9lua_Statei");
	lua_getfenv            = swordigo_dlsym("_Z11lua_getfenvP9lua_Statei");

	lua_settable           = swordigo_dlsym("_Z12lua_settableP9lua_Statei");
	lua_setfield           = swordigo_dlsym("_Z12lua_setfieldP9lua_StateiPKc");
	lua_rawset             = swordigo_dlsym("_Z10lua_rawsetP9lua_Statei");
	lua_rawseti            = swordigo_dlsym("_Z11lua_rawsetiP9lua_Stateii");
	lua_setmetatable       = swordigo_dlsym("_Z16lua_setmetatableP9lua_Statei");
	lua_setfenv            = swordigo_dlsym("_Z11lua_setfenvP9lua_Statei");

	lua_call               = swordigo_dlsym("_Z8lua_callP9lua_Stateii");
	lua_pcall              = swordigo_dlsym("_Z9lua_pcallP9lua_Stateiii");
	lua_cpcall             = swordigo_dlsym("_Z10lua_cpcallP9lua_StatePFiS0_EPv");
	lua_load               = swordigo_dlsym("_Z8lua_loadP9lua_StatePFPKcS0_PvPmES3_S2_");
	lua_dump               = swordigo_dlsym("_Z8lua_dumpP9lua_StatePFiS0_PKvmPvES3_");

	lua_yield              = swordigo_dlsym("_Z9lua_yieldP9lua_Statei");
	lua_resume             = swordigo_dlsym("_Z10lua_resumeP9lua_Statei");
	lua_status             = swordigo_dlsym("_Z10lua_statusP9lua_State");

	lua_gc                 = swordigo_dlsym("_Z6lua_gcP9lua_Stateii");

	lua_error              = swordigo_dlsym("_Z9lua_errorP9lua_State");
	lua_next               = swordigo_dlsym("_Z8lua_nextP9lua_Statei");
	lua_concat             = swordigo_dlsym("_Z10lua_concatP9lua_Statei");
	lua_getallocf          = swordigo_dlsym("_Z13lua_getallocfP9lua_StatePPv");
	lua_setallocf          = swordigo_dlsym("_Z13lua_setallocfP9lua_StatePFPvS1_S1_mmES1_");

	lua_setlevel           = swordigo_dlsym("_Z12lua_setlevelP9lua_StateS0_");
	lua_getstack           = swordigo_dlsym("_Z12lua_getstackP9lua_StateiP9lua_Debug");
	lua_getinfo            = swordigo_dlsym("_Z11lua_getinfoP9lua_StatePKcP9lua_Debug");
	lua_getlocal           = swordigo_dlsym("_Z12lua_getlocalP9lua_StatePK9lua_Debugi");
	lua_setlocal           = swordigo_dlsym("_Z12lua_setlocalP9lua_StatePK9lua_Debugi");
	lua_getupvalue         = swordigo_dlsym("_Z14lua_getupvalueP9lua_Stateii");
	lua_setupvalue         = swordigo_dlsym("_Z14lua_setupvalueP9lua_Stateii");
	lua_sethook            = swordigo_dlsym("_Z11lua_sethookP9lua_StatePFvS0_P9lua_DebugEii");
	lua_gethook            = swordigo_dlsym("_Z11lua_gethookP9lua_State");
	lua_gethookmask        = swordigo_dlsym("_Z15lua_gethookmaskP9lua_State");
	lua_gethookcount       = swordigo_dlsym("_Z16lua_gethookcountP9lua_State");
}