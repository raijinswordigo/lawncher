#include <string.h>
#include <stdio.h>
#include "Component.h"
#include "log.h"

#define LOG_TAG "ComponentSystem"

Component* component_fetch(SceneObject *obj, const char *component_name) {
	// _ZN5Caver[n][ComponentID]9InterfaceEv
	// _ZN5Caver14ModelComponent9InterfaceEv

	char interface_id[256];
	int len = (int)strlen(component_name);
	snprintf(interface_id, sizeof(interface_id), "_ZN5Caver%d%s9InterfaceEv", len, component_name);
	void *interface = swordigo_dlsym(interface_id);
	if (!interface) return NULL;
	void *comp = SceneObject_ComponentWithInterface(obj, interface);
	if (!comp) return NULL;
	LOGD("Fetched a component: %s", component_name);

	return comp;
}