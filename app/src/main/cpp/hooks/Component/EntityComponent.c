#include "Component/EntityComponent.h"
#include "Component.h"

EntityComponent *entityComponent_get(SceneObject *obj) {
	return (EntityComponent *)component_fetch(obj, "EntityComponent");
}

HOOK_SYMBOL(
	Update,
	"_ZN5Caver15EntityComponent6UpdateEf",
	void, (EntityComponent *comp, float dt)
) {
	comp->physics.maxSpeedFactor = 2000.0f;
	return orig_Update(comp, dt);
}