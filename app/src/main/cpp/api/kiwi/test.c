#include "lua.h"
#include "main/SceneObject.h"
#include "components.h"
#include "log.h"

#define LOG_TAG "KiwiTest"

int miniLL_test(lua_State *L) {
	void **hero = lua_touserdata(L, 1);
	void *cc = SceneObject_ComponentWithInterface(*hero, CharController_Interface);
	LOGD("cc %p", cc);
	*$(int, cc, 0x158, 0x260) = 0; /* works! */
	/* TODO: Complete the components system:
	 * @file caver/components.c
	 * \ref components.c
	 */
	lua_pushstring(L, "Hii!");
	return 1;
}