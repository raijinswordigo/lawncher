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
	return orig_Update(comp, dt);
}