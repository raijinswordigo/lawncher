#include <pthread.h>
#include "hook.h"
#include "log.h"
#include "lua.h"
#include "map.h"
#include <stdbool.h>

#define LOG_TAG "ProgramPatch"

#ifdef __arm__            // 32-bit only

HOOK_SYMBOL(
	LoadIntoState,
	"_ZNK5Caver7Program13LoadIntoStateEP9lua_State",
	int, (void *this, void *L)
) {
	void **string_px = (void **)((uintptr_t)this + 0x04);
	void **string_pn = (void **)((uintptr_t)this + 0x08);
	void **bytes_px  = (void **)((uintptr_t)this + 0x0c);
	void **bytes_pn  = (void **)((uintptr_t)this + 0x10);

	LOGD("Try Load (Lawda).");

	if (*string_px != NULL) {
		char *data = (char *)**(void ***)string_px;
		if (data != NULL) {
			uint32_t len = *(uint32_t *)(data - 0xc); /* libstdc++ size */
			if (len > 0) {
				LOGI("LoadIntoState: forcing String (len=%u), ignoring Bytes", len);

				// lemme try ts
				void *saved_px = *bytes_px;
				void *saved_pn = *bytes_pn;

				*bytes_px = *string_px;
				*bytes_pn = *string_pn;

				int ok = orig_LoadIntoState(this, L);

				/* Restore */
				*bytes_px = saved_px;
				*bytes_pn = saved_pn;

				return ok;
			}
		}
	}

	/* String missing or empty → do not load any code */
	LOGI("LoadIntoState: String empty/absent – skipping load (ignoring Bytes)");
	return 0;
}

#endif /* __arm__ */

/* TODO: Rename file to lua_patches */

static Map *g_ps_map = NULL;
static pthread_mutex_t g_ps_lock = PTHREAD_MUTEX_INITIALIZER;

void ps_setTimeScaleEnabled(void *ps, bool enabled) {
	if (!ps) return;
	pthread_mutex_lock(&g_ps_lock);
	if (!g_ps_map) {
		g_ps_map = Map_Create(1);
	}
	Map_Set(g_ps_map, ps, (void *)(uintptr_t)enabled);
	pthread_mutex_unlock(&g_ps_lock);
}

bool ps_isTimeScaleEnabled(void *ps) {
	if (!ps) return false;
	pthread_mutex_lock(&g_ps_lock);
	void *val = Map_Get(g_ps_map, ps);
	pthread_mutex_unlock(&g_ps_lock);
	return (bool)(uintptr_t)val;
}

void ps_remove(void *ps) {
	if (!ps) return;
	pthread_mutex_lock(&g_ps_lock);
	if (g_ps_map) {
		Map_Remove(g_ps_map, ps);
	}
	pthread_mutex_unlock(&g_ps_lock);
}

HOOK_SYMBOL(
	ProgramState_Update,
	"_ZN5Caver12ProgramState6UpdateEf",
	void, (void *ps, float dt)
) {
	if (!ps) {
		LOGE("ProgramState is NULL?");
		return;
	}

	lua_State *L = *$(lua_State *, ps, 0x0, 0x0);
	char aborted = *$(char, ps, 0x33, 0x5b);
	orig_ProgramState_Update(ps, dt);

	aborted = *$(char, ps, 0x33, 0x5b);
	if (aborted && L) {
		if (lua_gettop(L) > 0 && lua_isstring(L, -1)) {
			LOGE("%p encountered error: %s", ps, lua_tostring(L, -1));
		}
	}
}

HOOK_SYMBOL(
	ProgramState_DTor,
	"_ZN5Caver12ProgramStateD1Ev",
	void, (void *ps)
	) {
	ps_remove(ps);
	orig_ProgramState_DTor(ps);
}