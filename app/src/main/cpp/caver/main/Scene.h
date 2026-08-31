#ifndef LAWNCHER_SCENE_H
#define LAWNCHER_SCENE_H

#include "hook.h"
#include "types.h"

typedef struct Scene {
	char _pad0[archSplit(0x10, 0x20)];

	int pauseCount;
	float deltaTime;
	void *programState;

	char _pad1[archSplit(0x28, 0x48)];
	char isPaused;
	char _pad2[archSplit(0x14, 0x23)];

	void *debugRectList_prev;
	void *debugRectList_next;

	char _pad3[archSplit(0x3B, 0x64)];

	void *activeObjectsList_prev;
	void *activeObjectsList_next;

	int collisionShapesPending_count;
	int collisionShapesPending_capacity;
	void *collisionShapesPending_data;

	char collisionPairSet[archSplit(0x4, 0x8)];
	void *viewTransform;

	char _pad4[archSplit(0x10, 0x20)];

	void *pendingBoundsUpdateList_prev;
	void *pendingBoundsUpdateList_next;
	void *pendingActivationList_prev;
	void *pendingActivationList_next;

	char componentManagerActive[archSplit(0x30, 0x60)];
	char componentManagerInactive[archSplit(0x30, 0x60)];

	void *camera;
	char _pad5[archSplit(0x4, 0x8)];
	int glStateCache;
	char _pad6[archSplit(0x2, 0x4)];
	char lightOverlay[archSplit(0x10, 0x20)];

	int ambientColorPacked0;
	int ambientColorPacked1;
	char modulatingColorEnabled;
	char _pad7[archSplit(0x2, 0x3)];
	FloatColor modulatingColor;
	char hideOverlays;

	char _pad8[archSplit(0x5E, 0x7B)];

	int updateFrameCounter;
	Rectangle cameraBounds;
	float zRangeMin, zRangeMax;
	Rectangle cameraBoundsExpanded;

	void *worldBoundsUpdate_begin;
	void *worldBoundsUpdate_end;
	char hitboxes;
	char _pad9[archSplit(0x7, 0x13)];

	char mainSceneGrid[archSplit(0x34, 0x40)];
	char collisionSceneGrid[archSplit(0x34, 0x40)];

	char _pad10[archSplit(0x24, 0x28)];
} Scene;

Scene *get_scene();

#endif //LAWNCHER_SCENE_H
