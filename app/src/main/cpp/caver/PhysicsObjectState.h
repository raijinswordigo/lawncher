#ifndef LAWNCHER_PHYSICSOBJECTSTATE_H
#define LAWNCHER_PHYSICSOBJECTSTATE_H

#include "hook.h"
#include "SceneObject.h"

typedef struct PhysicsObjectState {
	SceneObject* owner;
	float airTime; // Increments when you walk off a ledge
	float airTime2;
	float groundNormalX;
	float groundNormalY;
	bool onSteepGround;
	char _pad0[archSplit(0x3, 0x7)];
	void* groundObject;
	char _pad1[archSplit(0x8, 0x8)];
	float groundFriction;
	float maxSpeedFactor;
	float accel;
	float friction;
	char _pad2[archSplit(0x30, 0x30)];
	float maxSpeed;
	bool enabled;
	char _pad3[archSplit(0x3, 0x3)];
	float forceY;
	bool flag;
	char _pad4[archSplit(0x3, 0x3)];
} PhysicsObjectState; // sizeof(0x74, 0x80)

#endif //LAWNCHER_PHYSICSOBJECTSTATE_H
