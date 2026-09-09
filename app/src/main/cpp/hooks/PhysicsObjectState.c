#include "PhysicsObjectState.h"

//#include "log.h"
//#include <string.h>
//#define LOG_TAG "PhysicsObjectState"

HOOK_SYMBOL(
	Update,
	"_ZN5Caver18PhysicsObjectState6UpdateEf",
	void, (PhysicsObjectState *state, float dt)
) {
//	SceneObject *owner = state->owner;
//	const char *id = String_get(&owner->Identifier);
////	LOGD("ID: '%s'", id);
//	if (strcmp(id, "hero") == 0) {
//		if (state->airTime > 0.05 || state->airTime < 340282346638528859811704183484516925440.0f) LOGD("Airtime: %f", state->airTime);
//	}
	return orig_Update(state, dt);
}