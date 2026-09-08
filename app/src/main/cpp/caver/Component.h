#ifndef LAWNCHER_COMPONENT_H
#define LAWNCHER_COMPONENT_H

#include "stdstring.h"
#include "SceneObject.h"

/*
 * Components are present within SceneObjects and
 * usually define the behaviour and utilities of an object.
 *
 * They can be accessed via
 * `SceneObject_ComponentWithInterface` using an interface handle,
 * `component_fetch` using the component's name like `ModelComponent`.
 */
typedef struct Component {
	void *vtable; // 0, 0
	int unknown0; // 4, 8
	char _pad0[archSplit(0x0, 0x4)]; // 8, 12
	void *vtable2; // 8, 16
	void *dat; // 12, 24
	int flags; // 16, 32
	int flags1; // 20, 36
	SceneObject *object; // 24 (0x18), 40 (0x28)
	String label; // 28 (0x1c), 48 (0x30)
} Component; // sizeof should be 0x28, 0x48
// this = operator_new(0x28); Component::Component(this);
// this_00 = operator_new(0x48); Component::Component(this_00);

Component* component_fetch(SceneObject *obj, const char *component_name);

#endif //LAWNCHER_COMPONENT_H
