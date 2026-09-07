#ifndef LAWNCHER_COMPONENT_H
#define LAWNCHER_COMPONENT_H

#include "stdstring.h"
#include "SceneObject.h"

typedef struct Component {
	void *vtable;
	char _pad0[archSplit(0x04, 0x08)];
	void *vtable2;
	void *dat;
	int flags;
	int flags2;
	SceneObject *object;
	char _pad1[archSplit(0x04, 0x08)];
	String label; // needs further testing...
	void *outletBindingsRoot;
	char _pad2[archSplit(0x04, 0x08)];
	long outletBindingsCount;
} Component; // sizeof should be 0x28, 0x48
// this = operator_new(0x28); Component::Component(this);
// this_00 = operator_new(0x48); Component::Component(this_00);

#endif //LAWNCHER_COMPONENT_H
