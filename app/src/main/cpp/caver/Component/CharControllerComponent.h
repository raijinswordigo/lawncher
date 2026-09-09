#ifndef LAWNCHER_CHARCONTROLLERCOMPONENT_H
#define LAWNCHER_CHARCONTROLLERCOMPONENT_H

#include "hook.h"

typedef struct CharControllerComponent {
	void* vtable;
	char _pad0[archSplit(0x11c, 0x21c)];
	int facingDirection;
	int moveDirection;
	bool isMoving;
	char _pad1[archSplit(0x17, 0x17)];
	bool isJumping;
	char _pad2[archSplit(0x3, 0x3)];
	int jumpState;
	int jumpTimer;
	char _pad3[archSplit(0x4, 0x4)];
	bool isFalling;
	char _pad4[archSplit(0x3, 0x3)];
	int airJumpCount;
	char _pad5[archSplit(0x8, 0x8)];
	float jumpSpeed;
	char _pad6[archSplit(0x8, 0x8)];
	float runSpeed;
	char _pad7[archSplit(0x4, 0x4)];
	float fastRunSpeed;
	char _pad8[archSplit(0x4c, 0x68)];
	bool isCasting;
	char _pad9[archSplit(0x3, 0x3)];
	void* skillComponent;
	float cooldown;
	char _pad10[archSplit(0x8, 0xc)];
} CharControllerComponent; // sizeof(0x1d8, 0x300)

#endif //LAWNCHER_CHARCONTROLLERCOMPONENT_H
